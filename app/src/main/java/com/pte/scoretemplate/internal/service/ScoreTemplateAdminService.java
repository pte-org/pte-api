package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoreTemplateStatus;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.domain.enums.TimingMode;
import com.pte.scoretemplate.dto.request.ImportScoreTemplateRequest;
import com.pte.scoretemplate.dto.request.ReplaceScoreTemplateItemsRequest;
import com.pte.scoretemplate.dto.request.ScoreTemplateItemRequest;
import com.pte.scoretemplate.dto.response.ScoreTemplateResponse;
import com.pte.scoretemplate.internal.exception.ScoreTemplateConcurrentModificationException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotDraftException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateNotFoundException;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import com.pte.scoretemplate.internal.mapper.ScoreTemplateMapper;
import com.pte.scoretemplate.internal.repository.ScoreTemplateRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Platform-admin-only CRUD/lifecycle for {@link ScoreTemplate}. Every HTTP
 * endpoint calling this is gated {@code PLATFORM_ADMIN} at the controller
 * (see {@code ScoreTemplateController}); this service has no tenant/caller
 * check of its own because a {@code ScoreTemplate} is a global, not
 * tenant-scoped, resource (FR-03).
 */
@Service
public class ScoreTemplateAdminService {

    private final ScoreTemplateRepository repository;

    public ScoreTemplateAdminService(ScoreTemplateRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<ScoreTemplateResponse> listAll() {
        return repository.findAllByOrderByCodeAscVersionDesc().stream().map(ScoreTemplateMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ScoreTemplateResponse getForEdit(UUID publicId) {
        return ScoreTemplateMapper.toResponse(findByPublicId(publicId));
    }

    /** Imports a UI-exported template as a new DRAFT in the same code family. */
    @Transactional
    public ScoreTemplateResponse importAsDraft(ImportScoreTemplateRequest request) {
        String code = request.code().trim();
        repository.findAllByCodeForUpdate(code);
        int nextVersion = repository.findMaxVersionByCode(code) + 1;

        ScoreTemplate draft = new ScoreTemplate();
        draft.setCode(code);
        draft.setVersion(nextVersion);
        draft.setName(request.name().trim());
        draft.setStatus(ScoreTemplateStatus.DRAFT);
        request.items().forEach(itemRequest -> draft.addItem(toEntity(itemRequest)));

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

    /** Replaces a DRAFT's name + full item list — no partial/incremental item edits (FR-02). */
    @Transactional
    public ScoreTemplateResponse replaceItems(UUID draftPublicId, ReplaceScoreTemplateItemsRequest request) {
        ScoreTemplate template = findByPublicId(draftPublicId);
        requireDraft(template);
        template.setName(request.name());
        template.getItems().clear();
        request.items().forEach(itemRequest -> template.addItem(toEntity(itemRequest)));
        return ScoreTemplateMapper.toResponse(repository.save(template));
    }

    /**
     * FR-04/FR-05: validates, then in one transaction retires whichever
     * template is currently ACTIVE (if any, and if not the target itself)
     * and activates the target. The partial unique ACTIVE index is the
     * final safety net if two admins race this concurrently — translated to
     * a clean 409 rather than a raw 500.
     */
    @Transactional
    public ScoreTemplateResponse activate(UUID publicId) {
        ScoreTemplate target = findByPublicId(publicId);
        ScoreTemplateActivationValidator.validate(target);

        repository.findAllByCodeForUpdate(target.getCode());
        repository.findWithItemsByStatus(ScoreTemplateStatus.ACTIVE)
                .filter(active -> !active.getPublicId().equals(target.getPublicId()))
                .ifPresent(active -> active.setStatus(ScoreTemplateStatus.RETIRED));
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
        copy.setTimingMode(source.getTimingMode());
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
        item.setTimingMode(parseEnum(TimingMode.class, request.timingMode(), request.taskType()));
        item.setScoringMethod(parseEnum(ScoringMethod.class, request.scoringMethod(), request.taskType()));
        item.setOverallWeight(request.overallWeight());
        item.setSpeakingWeight(request.speakingWeight());
        item.setWritingWeight(request.writingWeight());
        item.setReadingWeight(request.readingWeight());
        item.setListeningWeight(request.listeningWeight());
        return item;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, String taskType) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException ex) {
            throw new ScoreTemplateValidationException("Invalid " + type.getSimpleName() + " '" + value + "' for task type " + taskType);
        }
    }
}
