package com.pte.assessment.internal.service;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.itembank.ItembankService;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskRuntimeContractService;
import com.pte.itembank.TaskTypeObservability;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.assessment.dto.request.CreateExamDefinitionRequest;
import com.pte.assessment.dto.request.RejectExamDefinitionRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.request.ExamDefinitionItemRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.ExamDefinitionConcurrentModificationException;
import com.pte.assessment.internal.exception.ExamDefinitionNotDraftException;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Platform-owned score-template CRUD and review lifecycle. Draft authoring is
 * available to platform authors; activation remains an admin-only controller
 * operation because the template is global, not tenant-scoped.
 */
@Service
public class ExamDefinitionAdminService {

    private final ExamDefinitionRepository repository;
    private final QuestionTypeService questionTypeService;
    private final TaskRuntimeProfileService runtimeProfileService;
    private final TaskRuntimeContractService runtimeContractService;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;
    private final ItembankService itembankService;

    public ExamDefinitionAdminService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService) {
        this(repository, questionTypeService, null, null, null, null, null);
    }

    @Autowired
    public ExamDefinitionAdminService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService,
            TaskRuntimeProfileService runtimeProfileService, AuditLogService auditLogService,
            TaskRuntimeContractService runtimeContractService,
            TaskTypeObservability observability, ItembankService itembankService) {
        this.repository = repository;
        this.questionTypeService = questionTypeService;
        this.runtimeProfileService = runtimeProfileService;
        this.runtimeContractService = runtimeContractService;
        this.auditLogService = auditLogService;
        this.observability = observability;
        this.itembankService = itembankService;
    }

    /** Compatibility constructor for existing focused tests/callers. */
    public ExamDefinitionAdminService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService,
            TaskRuntimeProfileService runtimeProfileService, AuditLogService auditLogService) {
        this(repository, questionTypeService, runtimeProfileService, auditLogService, null, null, null);
    }

    /** Compatibility constructor used by focused tests and legacy in-process callers. */
    public ExamDefinitionAdminService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService,
            TaskRuntimeProfileService runtimeProfileService) {
        this(repository, questionTypeService, runtimeProfileService, null, null, null, null);
    }

    /** Compatibility constructor used by focused unit tests. */
    public ExamDefinitionAdminService(ExamDefinitionRepository repository) {
        this(repository, null, null, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public List<ExamDefinitionResponse> listAll() {
        return repository.findAllByOrderByCodeAscVersionDesc().stream().map(ExamDefinitionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExamDefinitionResponse getForEdit(UUID publicId) {
        return ExamDefinitionMapper.toResponse(findByPublicId(publicId));
    }

    @Transactional(readOnly = true)
    public boolean isCustomTemplate(UUID publicId) {
        return findByPublicId(publicId).getTemplatePolicy() == TemplatePolicy.CUSTOM;
    }

    /** Creates an empty DRAFT in the next version of a template code family. */
    @Transactional
    public ExamDefinitionResponse createDraft(CreateExamDefinitionRequest request) {
        return createDraft(request, null);
    }

    @Transactional
    public ExamDefinitionResponse createDraft(CreateExamDefinitionRequest request, CurrentUser caller) {
        requireDraftCreate(caller);
        String code = request.code().trim();
        repository.findAllByCodeForUpdate(code);
        int nextVersion = repository.findMaxVersionByCode(code) + 1;

        ExamDefinition draft = new ExamDefinition();
        draft.setCode(code);
        draft.setVersion(nextVersion);
        draft.setName(request.name().trim());
        draft.setStatus(ExamDefinitionStatus.DRAFT);
        draft.setTemplatePolicy(parsePolicy(request.templatePolicy()));
        draft.setAuthorUserPublicId(caller == null ? null : caller.userId());

        ExamDefinition saved = saveOrTranslateConflict(draft);
        audit(caller, ExamDefinitionConstants.AUDIT_CREATED, saved,
                "Created draft version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    /** Copies every item of {@code sourcePublicId} into a new DRAFT one version ahead, same {@code code}. */
    @Transactional
    public ExamDefinitionResponse cloneToDraft(UUID sourcePublicId) {
        return cloneToDraft(sourcePublicId, null);
    }

    @Transactional
    public ExamDefinitionResponse cloneToDraft(UUID sourcePublicId, CurrentUser caller) {
        requireDraftCreate(caller);
        ExamDefinition source = findByPublicId(sourcePublicId);
        // Locks every row of this code family first, closing the TOCTOU window
        // against a concurrent clone/activate on the same family (phase-01 Risks).
        repository.findAllByCodeForUpdate(source.getCode());
        int nextVersion = repository.findMaxVersionByCode(source.getCode()) + 1;

        ExamDefinition draft = new ExamDefinition();
        draft.setCode(source.getCode());
        draft.setVersion(nextVersion);
        draft.setName(source.getName());
        draft.setStatus(ExamDefinitionStatus.DRAFT);
        draft.setTemplatePolicy(source.getTemplatePolicy());
        draft.setAuthorUserPublicId(caller == null ? null : caller.userId());
        source.getItems().forEach(item -> draft.addItem(copyItem(item)));

        ExamDefinition saved = saveOrTranslateConflict(draft);
        audit(caller, ExamDefinitionConstants.AUDIT_CLONED, saved,
                "Cloned from version " + source.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
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
    public ExamDefinitionResponse replaceItems(UUID draftPublicId, ReplaceExamDefinitionItemsRequest request) {
        return replaceItems(draftPublicId, request, null);
    }

    @Transactional
    public ExamDefinitionResponse replaceItems(UUID draftPublicId, ReplaceExamDefinitionItemsRequest request,
            CurrentUser caller) {
        ExamDefinition template = findByPublicId(draftPublicId);
        requireDraft(template);
        requireDraftModification(caller, template);
        try {
            template.setName(request.name());
            if (request.templatePolicy() != null) {
                template.setTemplatePolicy(parsePolicy(request.templatePolicy()));
            }
            template.getItems().clear();
            request.items().forEach(itemRequest -> template.addItem(toEntity(itemRequest)));
            // Draft authoring is intentionally non-blocking for runtime
            // readiness. Valid profiles are pinned immediately, while a
            // missing/retired contract is retained as an unpinned item so the
            // response can show the actionable readiness problem. Preserve
            // the historical eager standard-weight check, but let CUSTOM
            // drafts be assembled incrementally.
            if (!isCustom(template)) {
                ExamDefinitionActivationValidator.validateSkillWeightTotals(template);
            }
            validateCatalog(template, false);
            ExamDefinition saved = repository.save(template);
            audit(caller, ExamDefinitionConstants.AUDIT_UPDATED, saved,
                    "Updated draft version " + saved.getVersion());
            return ExamDefinitionMapper.toResponse(saved);
        } catch (ExamDefinitionValidationException ex) {
            auditValidationFailure(caller, template, ex);
            throw ex;
        }
    }

    /** Author submits a complete, structurally valid draft for admin review. */
    @Transactional
    public ExamDefinitionResponse submitApproval(UUID publicId) {
        return submitApproval(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse submitApproval(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        requireDraft(template);
        requireDraftModification(caller, template);
        try {
            validateCatalog(template);
            ExamDefinitionActivationValidator.validate(template);
            validateQuestionBank(template);
        } catch (ExamDefinitionValidationException ex) {
            auditValidationFailure(caller, template, ex);
            throw ex;
        }
        template.setRejectionReason(null);
        template.setStatus(ExamDefinitionStatus.PENDING_APPROVAL);
        ExamDefinitionResponse response = ExamDefinitionMapper.toResponse(repository.save(template));
        audit(caller, ExamDefinitionConstants.AUDIT_SUBMITTED, template,
                "Submitted version " + template.getVersion() + " for approval");
        return response;
    }

    /** Admin approval returns the immutable review decision to an editable draft. */
    @Transactional
    public ExamDefinitionResponse approve(UUID publicId) {
        return approve(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse approve(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        requireReview(caller, template);
        requirePendingApproval(template);
        try {
            validateCatalog(template);
            ExamDefinitionActivationValidator.validate(template);
            validateQuestionBank(template);
        } catch (ExamDefinitionValidationException ex) {
            auditValidationFailure(caller, template, ex);
            throw ex;
        }
        template.setRejectionReason(null);
        template.setStatus(ExamDefinitionStatus.DRAFT);
        ExamDefinitionResponse response = ExamDefinitionMapper.toResponse(repository.save(template));
        audit(caller, ExamDefinitionConstants.AUDIT_APPROVED, template,
                "Approved version " + template.getVersion() + " for activation");
        return response;
    }

    /** Admin rejection returns the template to DRAFT with an actionable reason. */
    @Transactional
    public ExamDefinitionResponse reject(UUID publicId, RejectExamDefinitionRequest request) {
        return reject(publicId, request, null);
    }

    @Transactional
    public ExamDefinitionResponse reject(UUID publicId, RejectExamDefinitionRequest request, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        requireReview(caller, template);
        requirePendingApproval(template);
        template.setRejectionReason(request.reason().trim());
        template.setStatus(ExamDefinitionStatus.DRAFT);
        ExamDefinitionResponse response = ExamDefinitionMapper.toResponse(repository.save(template));
        audit(caller, ExamDefinitionConstants.AUDIT_REJECTED, template,
                "Returned version " + template.getVersion() + " to draft: " + template.getRejectionReason());
        return response;
    }

    /** Deletes a DRAFT; ACTIVE and RETIRED versions remain immutable and auditable. */
    @Transactional
    public void deleteDraft(UUID publicId) {
        deleteDraft(publicId, null);
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        requireDraft(template);
        requireDraftModification(caller, template);
        audit(caller, ExamDefinitionConstants.AUDIT_DELETED, template, "Deleted draft version " + template.getVersion());
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
    public ExamDefinitionResponse activate(UUID publicId) {
        return activate(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse activate(UUID publicId, CurrentUser caller) {
        ExamDefinition target = findByPublicId(publicId);
        requireDraft(target);
        requirePublish(caller, target);
        try {
            validateCatalog(target);
            ExamDefinitionActivationValidator.validate(target);
            validateQuestionBank(target);
        } catch (ExamDefinitionValidationException ex) {
            auditValidationFailure(caller, target, ex);
            throw ex;
        }

        repository.findAllByCodeForUpdate(target.getCode());
        repository.findWithItemsByStatus(ExamDefinitionStatus.ACTIVE)
                .filter(active -> !active.getPublicId().equals(target.getPublicId()))
                .ifPresent(active -> {
                    active.setStatus(ExamDefinitionStatus.RETIRED);
                    repository.saveAndFlush(active);
                    audit(caller, ExamDefinitionConstants.AUDIT_RETIRED, active,
                            "Retired version " + active.getVersion() + " after activating version "
                                    + target.getVersion());
                });
        target.setStatus(ExamDefinitionStatus.ACTIVE);

        ExamDefinition saved = saveOrTranslateConflict(target);
        if (questionTypeService != null) {
            questionTypeService.recordPublishedUse(
                    target.getItems().stream().map(this::logicalTaskKey).distinct().toList(), Instant.now());
            audit(caller, ExamDefinitionConstants.AUDIT_PUBLICATION_USAGE_RECORDED, target,
                    "Recorded runtime lock usage for " + target.getItems().stream()
                            .map(this::logicalTaskKey).distinct().sorted().toList());
        }
        audit(caller, ExamDefinitionConstants.AUDIT_ACTIVATED, saved,
                "Activated version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    private ExamDefinition findByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId).orElseThrow(ExamDefinitionNotFoundException::new);
    }

    private void requireDraft(ExamDefinition template) {
        if (template.getStatus() != ExamDefinitionStatus.DRAFT) {
            throw new ExamDefinitionNotDraftException();
        }
    }

    private void requirePendingApproval(ExamDefinition template) {
        if (template.getStatus() != ExamDefinitionStatus.PENDING_APPROVAL) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.TEMPLATE_NOT_PENDING_APPROVAL);
        }
    }

    private void requireDraftCreate(CurrentUser caller) {
        if (caller != null && !SecurityPolicy.canCreateAcademicDraft(caller)) {
            deny(caller, null, ExamDefinitionConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireDraftModification(CurrentUser caller, ExamDefinition template) {
        if (caller != null && !SecurityPolicy.canModifyAcademicDraft(caller, template.getAuthorUserPublicId())) {
            deny(caller, template, ExamDefinitionConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireReview(CurrentUser caller, ExamDefinition template) {
        if (caller == null || SecurityPolicy.canReviewAcademic(caller, template.getAuthorUserPublicId())) {
            return;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && template.getAuthorUserPublicId() != null
                && template.getAuthorUserPublicId().equals(caller.userId())) {
            deny(caller, template, ExamDefinitionConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, template, ExamDefinitionConstants.ACADEMIC_REVIEW_REQUIRED);
    }

    private void requirePublish(CurrentUser caller, ExamDefinition template) {
        if (caller == null || SecurityPolicy.canPublishAcademic(caller, template.getAuthorUserPublicId())) {
            return;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && template.getAuthorUserPublicId() != null
                && template.getAuthorUserPublicId().equals(caller.userId())) {
            deny(caller, template, ExamDefinitionConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, template, ExamDefinitionConstants.ACADEMIC_PUBLISH_REQUIRED);
    }

    private void deny(CurrentUser caller, ExamDefinition template, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                    template == null ? "unknown" : String.valueOf(template.getPublicId()),
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
    }

    private boolean isCustom(ExamDefinition template) {
        return template.getTemplatePolicy() == TemplatePolicy.CUSTOM;
    }

    private void validateCatalog(ExamDefinition template) {
        validateCatalog(template, true);
    }

    private void validateCatalog(ExamDefinition template, boolean strict) {
        if (questionTypeService == null) {
            return;
        }
        boolean customPolicy = template.getTemplatePolicy() == TemplatePolicy.CUSTOM;
        List<String> errors = new ArrayList<>();
        List<ExamDefinitionItem> profileItems = new ArrayList<>();
        List<String> missingProfileCodes = new ArrayList<>();
        for (ExamDefinitionItem item : template.getItems()) {
            String logicalKey;
            try {
                logicalKey = logicalTaskKey(item);
                if (logicalKey == null || logicalKey.isBlank()) {
                    throw new IllegalArgumentException(ExamDefinitionConstants.MISSING_TASK_TYPE_KEY);
                }
                logicalKey = TaskTypeCodeCompatibility.normalizeTaskTypeKey(logicalKey);
                item.setTaskTypeKey(logicalKey);
                if (TaskTypeCodeCompatibility.isStandard(logicalKey)) {
                    item.setTaskType(logicalKey);
                } else {
                    item.setTaskType(null);
                }
            } catch (RuntimeException ex) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalTaskKey(item));
                continue;
            }
            if (!questionTypeService.isActive(logicalKey)) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalKey);
                continue;
            }
            String expectedSection = questionTypeService.findSectionByTaskTypeKey(logicalKey)
                    .orElse(standardSection(logicalKey));
            if (expectedSection == null || !expectedSection.equals(item.getSection())) {
                errors.add(ExamDefinitionConstants.TEMPLATE_SECTION_INVALID + logicalKey);
                continue;
            }
            if (!customPolicy && !TaskTypeCodeCompatibility.isStandard(logicalKey)) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + logicalKey);
                continue;
            }
            profileItems.add(item);
            if (item.pinnedRuntimeProfile() == null) {
                missingProfileCodes.add(logicalKey);
            }
        }

        Map<String, TaskRuntimeProfileDescriptor> activeProfiles = new LinkedHashMap<>();
        boolean activeProfileLookupFailed = false;
        if (!missingProfileCodes.isEmpty()) {
            try {
                if (runtimeProfileService == null) {
                    for (String code : missingProfileCodes) {
                        activeProfiles.put(code, resolveActiveProfile(code));
                    }
                } else {
                    for (String code : missingProfileCodes) {
                        activeProfiles.put(code, resolveActiveProfile(code));
                    }
                }
            } catch (RuntimeException ex) {
                activeProfileLookupFailed = true;
            }
        }

        for (ExamDefinitionItem item : profileItems) {
            if (item.pinnedRuntimeProfile() == null) {
                TaskRuntimeProfileDescriptor active = activeProfiles.get(logicalTaskKey(item));
                if (!activeProfileLookupFailed && active != null) {
                    item.pinRuntimeProfile(active);
                }
            }
        }

        List<TaskRuntimeProfileDescriptor> pinnedProfiles = profileItems.stream()
                .map(ExamDefinitionItem::pinnedRuntimeProfile)
                .filter(java.util.Objects::nonNull)
                .toList();
        Set<TaskRuntimeProfileDescriptor> invalidProfiles = profileItems.stream()
                .filter(item -> !customPolicy || TaskTypeCodeCompatibility.isStandard(logicalTaskKey(item)))
                .map(ExamDefinitionItem::pinnedRuntimeProfile)
                .filter(java.util.Objects::nonNull)
                .filter(profile -> !isAllowlistedProfile(profile))
                .collect(java.util.stream.Collectors.toSet());
        for (ExamDefinitionItem item : profileItems) {
            if (item.pinnedRuntimeProfile() == null || invalidProfiles.contains(item.pinnedRuntimeProfile())) {
                errors.add(ExamDefinitionConstants.TEMPLATE_PROFILE_INVALID + logicalTaskKey(item));
                continue;
            }
            try {
                if (item.getScoringMethod() != TaskTypeScoringMethods.resolve(item.pinnedRuntimeProfile())) {
                    errors.add(ExamDefinitionConstants.TEMPLATE_SCORING_PROFILE_INVALID + logicalTaskKey(item));
                }
            } catch (RuntimeException ex) {
                errors.add(ExamDefinitionConstants.TEMPLATE_SCORING_PROFILE_INVALID + logicalTaskKey(item));
            }
        }
        if (strict && !errors.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", errors));
        }
    }

    private void validateQuestionBank(ExamDefinition template) {
        if (itembankService == null) {
            return;
        }
        Set<String> taskTypeKeys = template.getItems().stream()
                .map(this::logicalTaskKey)
                .filter(key -> key != null && !key.isBlank())
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        Map<String, Long> available = itembankService.countPublishedByTaskTypeKeys(taskTypeKeys);
        List<String> shortages = template.getItems().stream()
                .filter(item -> available.getOrDefault(logicalTaskKey(item), 0L) < item.getMaxCount())
                .map(item -> ExamDefinitionConstants.TEMPLATE_QUESTION_BANK_INSUFFICIENT
                        + logicalTaskKey(item) + " (required " + item.getMaxCount() + ", available "
                        + available.getOrDefault(logicalTaskKey(item), 0L) + ")")
                .toList();
        if (!shortages.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", shortages));
        }
    }

    private boolean isAllowlistedProfile(TaskRuntimeProfileDescriptor profile) {
        return TaskRuntimeProfileRegistry.isAllowlistedContract(profile);
    }

    private String standardSection(String logicalKey) {
        try {
            return TaskTypeCodeCompatibility.isStandard(logicalKey)
                    ? PteTaskType.valueOf(logicalKey).getSection().name()
                    : null;
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private void audit(CurrentUser caller, String action, ExamDefinition template, String summary) {
        if (auditLogService == null || caller == null || template == null) {
            return;
        }
        auditLogService.record(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                String.valueOf(template.getPublicId()), action, truncate(summary));
    }

    private void auditValidationFailure(CurrentUser caller, ExamDefinition template,
            ExamDefinitionValidationException exception) {
        if (observability != null) {
            observability.templateReadinessFailed();
        }
        if (auditLogService == null || caller == null || template == null) {
            return;
        }
        auditLogService.recordFailure(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                String.valueOf(template.getPublicId()), ExamDefinitionConstants.AUDIT_VALIDATION_FAILED,
                truncate(exception.getMessage()));
    }

    private String truncate(String summary) {
        if (summary == null) {
            return "";
        }
        return summary.length() <= 500 ? summary : summary.substring(0, 500);
    }

    private ExamDefinition saveOrTranslateConflict(ExamDefinition template) {
        try {
            return repository.save(template);
        } catch (DataIntegrityViolationException | org.springframework.dao.OptimisticLockingFailureException ex) {
            throw new ExamDefinitionConcurrentModificationException();
        }
    }

    private ExamDefinitionItem copyItem(ExamDefinitionItem source) {
        ExamDefinitionItem copy = new ExamDefinitionItem();
        copy.setTaskType(source.getTaskType() == null ? null
                : TaskTypeCodeCompatibility.normalizeForLookup(source.getTaskType()));
        copy.setTaskTypeKey(logicalTaskKey(source));
        copy.setSection(source.getSection());
        copy.setSequence(source.getSequence());
        copy.setMinCount(source.getMinCount());
        copy.setMaxCount(source.getMaxCount());
        copy.setPrepSeconds(source.getPrepSeconds());
        copy.setResponseSeconds(source.getResponseSeconds());
        copy.setOverallWeight(source.getOverallWeight());
        copy.setSpeakingWeight(source.getSpeakingWeight());
        copy.setWritingWeight(source.getWritingWeight());
        copy.setReadingWeight(source.getReadingWeight());
        copy.setListeningWeight(source.getListeningWeight());
        TaskRuntimeProfileDescriptor pinned = source.pinnedRuntimeProfile();
        pinned = pinned == null ? resolveActiveProfile(copy.getTaskTypeKey()) : pinned;
        copy.pinRuntimeProfile(pinned);
        copy.setScoringMethod(TaskTypeScoringMethods.resolve(pinned));
        return copy;
    }

    private ExamDefinitionItem toEntity(ExamDefinitionItemRequest request) {
        ExamDefinitionItem item = new ExamDefinitionItem();
        final String canonicalTaskType;
        try {
            String rawKey = request.taskTypeKey() == null || request.taskTypeKey().isBlank()
                    ? request.taskType() : request.taskTypeKey();
            canonicalTaskType = TaskTypeCodeCompatibility.normalizeTaskTypeKey(rawKey);
        } catch (RuntimeException ex) {
            throw new ExamDefinitionValidationException(
                    ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + (request.taskTypeKey() == null
                            ? request.taskType() : request.taskTypeKey()));
        }
        item.setTaskType(TaskTypeCodeCompatibility.isStandard(canonicalTaskType) ? canonicalTaskType : null);
        item.setTaskTypeKey(canonicalTaskType);
        final TaskRuntimeProfileDescriptor runtimeProfile;
        try {
            runtimeProfile = resolveActiveProfile(canonicalTaskType);
            item.pinRuntimeProfile(runtimeProfile);
        } catch (RuntimeException ex) {
            // A DRAFT can be saved before the selected task type has an
            // active runtime contract. Keep the row persistable and let the
            // strict submit/activation validation report the missing profile.
            item.setScoringMethod(com.pte.assessment.domain.enums.ScoringMethod.UNSCORED);
            item.setSection(request.section());
            item.setSequence(request.sequence());
            item.setMinCount(request.minCount());
            item.setMaxCount(request.maxCount());
            item.setPrepSeconds(request.prepSeconds());
            item.setResponseSeconds(request.responseSeconds());
            item.setSpeakingWeight(request.speakingWeight());
            item.setWritingWeight(request.writingWeight());
            item.setReadingWeight(request.readingWeight());
            item.setListeningWeight(request.listeningWeight());
            item.setOverallWeight(computeOverallWeight(
                    request.speakingWeight(), request.writingWeight(), request.readingWeight(),
                    request.listeningWeight()));
            return item;
        }
        item.setSection(request.section());
        item.setSequence(request.sequence());
        item.setMinCount(request.minCount());
        item.setMaxCount(request.maxCount());
        item.setPrepSeconds(request.prepSeconds());
        item.setResponseSeconds(request.responseSeconds());
        item.setScoringMethod(TaskTypeScoringMethods.resolve(runtimeProfile));
        item.setSpeakingWeight(request.speakingWeight());
        item.setWritingWeight(request.writingWeight());
        item.setReadingWeight(request.readingWeight());
        item.setListeningWeight(request.listeningWeight());
        item.setOverallWeight(computeOverallWeight(
                request.speakingWeight(), request.writingWeight(), request.readingWeight(), request.listeningWeight()));
        return item;
    }

    private TaskRuntimeProfileDescriptor resolveActiveProfile(String taskTypeCode) {
        if (TaskTypeCodeCompatibility.isStandard(taskTypeCode)) {
            return runtimeProfileService == null
                    ? TaskRuntimeProfileRegistry.descriptorFor(taskTypeCode)
                    : runtimeProfileService.resolveActive(taskTypeCode);
        }
        if (questionTypeService == null) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + taskTypeCode);
        }
        TaskRuntimeContractDescriptor contract = questionTypeService.resolveRuntimeContract(taskTypeCode);
        return new TaskRuntimeProfileDescriptor(taskTypeCode, contract.profileKey(), contract.profileVersion(),
                contract.behaviorKey(), contract.rendererKey(), contract.answerSchemaVersion(),
                contract.scoringProfileKey(), contract.scoringProfileVersion(),
                contract.requiredClientCapabilities(), contract.status(), contract.screenKey(),
                contract.contractVersion(), contract.scoringMode(), contract.minSupportedAppVersion(),
                contract.authoringContractKey(), contract.authoringContractVersion());
    }

    private String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType())
                : TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.getTaskTypeKey());
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
     * see {@link ExamDefinitionActivationValidator} for why OVERALL is
     * exempt from the exact-100 check the other 4 columns get.
     */
    private static BigDecimal computeOverallWeight(
            BigDecimal speakingWeight, BigDecimal writingWeight, BigDecimal readingWeight, BigDecimal listeningWeight) {
        BigDecimal sum = speakingWeight.add(writingWeight).add(readingWeight).add(listeningWeight);
        return sum.divide(BigDecimal.valueOf(4), 2, RoundingMode.HALF_UP);
    }

    private TemplatePolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return TemplatePolicy.STANDARD_PTE;
        }
        try {
            return TemplatePolicy.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.UNKNOWN_TEMPLATE_POLICY + raw);
        }
    }
}
