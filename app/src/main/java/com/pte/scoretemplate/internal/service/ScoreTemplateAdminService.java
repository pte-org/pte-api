package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.scoretemplate.dto.request.CreateScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.RejectScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.request.ScoreTemplateItemRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.ScoreTemplateConcurrentModificationException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotDraftException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.scoretemplate.internal.mapper.ScoreTemplateMapper;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Platform-owned score-template CRUD and review lifecycle. Draft authoring is
 * available to platform authors; activation remains an admin-only controller
 * operation because the template is global, not tenant-scoped.
 */
@Service
public class ScoreTemplateAdminService {

    private final ScoreTemplateRepository repository;
    private final QuestionTypeService questionTypeService;

    @Autowired
    public ScoreTemplateAdminService(ScoreTemplateRepository repository, QuestionTypeService questionTypeService) {
        this.repository = repository;
        this.questionTypeService = questionTypeService;
    }

    /** Compatibility constructor used by focused unit tests. */
    public ScoreTemplateAdminService(ScoreTemplateRepository repository) {
        this.repository = repository;
        this.questionTypeService = null;
    }

    @Transactional(readOnly = true)
    public List<ScoreTemplateResponse> listAll() {
        return repository.findAllByOrderByCodeAscVersionDesc().stream().map(ScoreTemplateMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScoreTemplateResponse getForEdit(UUID publicId) {
        return ScoreTemplateMapper.toResponse(findByPublicId(publicId));
    }

    /** Creates an empty DRAFT in the next version of a template code family. */
    @Transactional
    public ScoreTemplateResponse createDraft(CreateScoreTemplateRequest request) {
        String code = request.code().trim();
        repository.findAllByCodeForUpdate(code);
        int nextVersion = repository.findMaxVersionByCode(code) + 1;

        ScoreTemplate draft = new ScoreTemplate();
        draft.setCode(code);
        draft.setVersion(nextVersion);
        draft.setName(request.name().trim());
        draft.setStatus(ScoreTemplateStatus.DRAFT);

        return ScoreTemplateMapper.toResponse(saveOrTranslateConflict(draft));
    }

    /** Copies every item of {@code sourcePublicId} into a new DRAFT one version ahead, same {@code code}. */
    @Transactional
    public ScoreTemplateResponse cloneToDraft(UUID sourcePublicId) {
        ScoreTemplate source = findByPublicId(sourcePublicId);
        // Locks every row of this code family first, closing the TOCTOU window
        // against a concurrent clone/activate on the same family (phase-01 Risks).
        repository.findAllByCodeForUpdate(source.getCode());
        int nextVersion = repository.findMaxVersionByCode(source.getCode()) + 1;

        ScoreTemplate draft = new ScoreTemplate();
        draft.setCode(source.getCode());
        draft.setVersion(nextVersion);
        draft.setName(source.getName());
        draft.setStatus(ScoreTemplateStatus.DRAFT);
        source.getItems().forEach(item -> draft.addItem(copyItem(item)));

        return ScoreTemplateMapper.toResponse(saveOrTranslateConflict(draft));
    }

    /**
     * Replaces a DRAFT's name + full item list — no partial/incremental item
     * edits (FR-02). Enforces the exact-100 skill-weight check on every
     * save (not only at {@link #activate}) so a DRAFT in the database is
     * never left with weights that don't add up — {@code overallWeight} is
     * recomputed here too (via {@code toEntity}), on every save, regardless
     * of whether this validation passes or the caller is mid-edit.
     */
    @Transactional
    public ScoreTemplateResponse replaceItems(UUID draftPublicId, ReplaceScoreTemplateItemsRequest request) {
        ScoreTemplate template = findByPublicId(draftPublicId);
        requireDraft(template);
        template.setName(request.name());
        template.getItems().clear();
        request.items().forEach(itemRequest -> template.addItem(toEntity(itemRequest)));
        ScoreTemplateActivationValidator.validateSkillWeightTotals(template);
        return ScoreTemplateMapper.toResponse(repository.save(template));
    }

    /** Author submits a complete, structurally valid draft for admin review. */
    @Transactional
    public ScoreTemplateResponse submitApproval(UUID publicId) {
        ScoreTemplate template = findByPublicId(publicId);
        requireDraft(template);
        validateCatalog(template);
        ScoreTemplateActivationValidator.validate(template);
        template.setRejectionReason(null);
        template.setStatus(ScoreTemplateStatus.PENDING_APPROVAL);
        return ScoreTemplateMapper.toResponse(repository.save(template));
    }

    /** Admin approval returns the immutable review decision to an editable draft. */
    @Transactional
    public ScoreTemplateResponse approve(UUID publicId) {
        ScoreTemplate template = findByPublicId(publicId);
        requirePendingApproval(template);
        validateCatalog(template);
        ScoreTemplateActivationValidator.validate(template);
        template.setRejectionReason(null);
        template.setStatus(ScoreTemplateStatus.DRAFT);
        return ScoreTemplateMapper.toResponse(repository.save(template));
    }

    /** Admin rejection returns the template to DRAFT with an actionable reason. */
    @Transactional
    public ScoreTemplateResponse reject(UUID publicId, RejectScoreTemplateRequest request) {
        ScoreTemplate template = findByPublicId(publicId);
        requirePendingApproval(template);
        template.setRejectionReason(request.reason().trim());
        template.setStatus(ScoreTemplateStatus.DRAFT);
        return ScoreTemplateMapper.toResponse(repository.save(template));
    }

    /** Deletes a DRAFT; ACTIVE and RETIRED versions remain immutable and auditable. */
    @Transactional
    public void deleteDraft(UUID publicId) {
        ScoreTemplate template = findByPublicId(publicId);
        requireDraft(template);
        repository.delete(template);
    }

    /**
     * FR-04/FR-05: validates, then in one transaction retires whichever
     * template is currently ACTIVE (if any, and if not the target itself)
     * and activates the target. The old row is retired via
     * {@code saveAndFlush} — not left to Hibernate's automatic dirty-check
     * flush — because the partial unique ACTIVE index sees both rows as
     * ACTIVE for an instant otherwise: Hibernate doesn't guarantee it flushes
     * the retire-UPDATE before the activate-UPDATE just because the source
     * mutates {@code active} first, and flushing them in the wrong order
     * trips the index mid-transaction even though the net result would have
     * been valid. That same index is still the final safety net for two
     * admins racing this concurrently — translated to a clean 409 rather
     * than a raw 500.
     */
    @Transactional
    public ScoreTemplateResponse activate(UUID publicId) {
        ScoreTemplate target = findByPublicId(publicId);
        requireDraft(target);
        validateCatalog(target);
        ScoreTemplateActivationValidator.validate(target);

        repository.findAllByCodeForUpdate(target.getCode());
        repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)
                .filter(active -> !active.getPublicId().equals(target.getPublicId()))
                .ifPresent(active -> {
                    active.setStatus(ScoreTemplateStatus.RETIRED);
                    repository.saveAndFlush(active);
                });
        target.setStatus(ScoreTemplateStatus.ACTIVE);

        return ScoreTemplateMapper.toResponse(saveOrTranslateConflict(target));
    }

    private ScoreTemplate findByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId).orElseThrow(ScoreTemplateNotFoundException::new);
    }

    private void requireDraft(ScoreTemplate template) {
        if (template.getStatus() != ScoreTemplateStatus.DRAFT) {
            throw new ScoreTemplateNotDraftException();
        }
    }

    private void requirePendingApproval(ScoreTemplate template) {
        if (template.getStatus() != ScoreTemplateStatus.PENDING_APPROVAL) {
            throw new ScoreTemplateValidationException(ScoreTemplateConstants.TEMPLATE_NOT_PENDING_APPROVAL);
        }
    }

    private void validateCatalog(ScoreTemplate template) {
        if (questionTypeService == null) {
            return;
        }
        template.getItems().forEach(item -> {
            final PteTaskType taskType;
            try {
                taskType = PteTaskType.valueOf(item.getTaskType());
            } catch (IllegalArgumentException ex) {
                throw new ScoreTemplateValidationException(
                        ScoreTemplateConstants.TEMPLATE_TASK_TYPE_INVALID + item.getTaskType());
            }
            if (!questionTypeService.isActive(taskType.name())) {
                throw new ScoreTemplateValidationException(
                        ScoreTemplateConstants.TEMPLATE_TASK_TYPE_INVALID + item.getTaskType());
            }
            if (!taskType.getSection().name().equals(item.getSection())) {
                throw new ScoreTemplateValidationException(
                        ScoreTemplateConstants.TEMPLATE_SECTION_INVALID + item.getTaskType());
            }
        });
    }

    private ScoreTemplate saveOrTranslateConflict(ScoreTemplate template) {
        try {
            return repository.save(template);
        } catch (DataIntegrityViolationException ex) {
            throw new ScoreTemplateConcurrentModificationException();
        }
    }

    private ScoreTemplateItem copyItem(ScoreTemplateItem source) {
        ScoreTemplateItem copy = new ScoreTemplateItem();
        copy.setTaskType(source.getTaskType());
        copy.setSection(source.getSection());
        copy.setSequence(source.getSequence());
        copy.setMinCount(source.getMinCount());
        copy.setMaxCount(source.getMaxCount());
        copy.setPrepSeconds(source.getPrepSeconds());
        copy.setResponseSeconds(source.getResponseSeconds());
        copy.setScoringMethod(source.getScoringMethod());
        copy.setOverallWeight(source.getOverallWeight());
        copy.setSpeakingWeight(source.getSpeakingWeight());
        copy.setWritingWeight(source.getWritingWeight());
        copy.setReadingWeight(source.getReadingWeight());
        copy.setListeningWeight(source.getListeningWeight());
        return copy;
    }

    private ScoreTemplateItem toEntity(ScoreTemplateItemRequest request) {
        ScoreTemplateItem item = new ScoreTemplateItem();
        item.setTaskType(request.taskType());
        item.setSection(request.section());
        item.setSequence(request.sequence());
        item.setMinCount(request.minCount());
        item.setMaxCount(request.maxCount());
        item.setPrepSeconds(request.prepSeconds());
        item.setResponseSeconds(request.responseSeconds());
        item.setScoringMethod(TaskTypeScoringMethods.resolve(request.taskType()));
        item.setSpeakingWeight(request.speakingWeight());
        item.setWritingWeight(request.writingWeight());
        item.setReadingWeight(request.readingWeight());
        item.setListeningWeight(request.listeningWeight());
        item.setOverallWeight(computeOverallWeight(
                request.speakingWeight(), request.writingWeight(), request.readingWeight(), request.listeningWeight()));
        return item;
    }

    /**
     * PTE weighs all 4 communicative skills equally toward the overall
     * score, so a task type's overall contribution is simply the mean of
     * its 4 skill weights — verified cell-by-cell against the APEUni V5
     * table (every one of the 22 rows matches exactly). Never admin input:
     * an admin typing a number here that didn't match this formula would
     * silently desync the displayed "Overall %" from what actually drives
     * scoring. Rounding independently per row (rather than distributing a
     * remainder) can leave the 22 rows summing to e.g. 100.01 instead of
     * 100.00 — harmless and expected, matching the source table itself;
     * see {@link ScoreTemplateActivationValidator} for why OVERALL is
     * exempt from the exact-100 check the other 4 columns get.
     */
    private static BigDecimal computeOverallWeight(
            BigDecimal speakingWeight, BigDecimal writingWeight, BigDecimal readingWeight, BigDecimal listeningWeight) {
        BigDecimal sum = speakingWeight.add(writingWeight).add(readingWeight).add(listeningWeight);
        return sum.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }
}
