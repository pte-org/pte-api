package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.TaskAuthoringRequirements;
import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.dto.request.CreateTaskTypeRequest;
import com.pte.itembank.dto.request.UpdateTaskTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.tasktype.InvalidQuestionTypeException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeCapabilityNotFoundException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeDisplayNameAlreadyUsedException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeDisplayNameInvalidException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeKeyAlreadyUsedException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeKeyInvalidException;
import com.pte.itembank.internal.exception.tasktype.TaskTypeRuntimeLockedException;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.tasktype.RuntimeContractResolver;
import com.pte.itembank.TaskRuntimeContractDescriptor;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeKeyNormalizer;
import com.pte.itembank.TaskTypeObservability;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.constant.SharedConstants;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Manages the lifecycle of custom, dynamic task types created through the
 * admin UI: create, update, submit, approve, retire.
 */
@Service
public class DynamicTaskTypeService {

    private final QuestionTypeRepository repository;
    private final RuntimeContractResolver contractResolver;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;

    public DynamicTaskTypeService(
            QuestionTypeRepository repository,
            RuntimeContractResolver contractResolver,
            AuditLogService auditLogService,
            TaskTypeObservability observability) {
        this.repository = repository;
        this.contractResolver = contractResolver;
        this.auditLogService = auditLogService;
        this.observability = observability;
    }

    /**
     * Create a new custom task type.
     */
    @Transactional
    public QuestionTypeResponse create(CreateTaskTypeRequest request, CurrentUser caller) {
        requireDraftWrite(caller);
        String taskTypeKey = normalizeKeyForApi(request.taskTypeKey());
        String normalizedDisplayName = normalizeDisplayNameForApi(request.displayName());
        if (repository.findByTaskTypeKey(taskTypeKey).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeKeyAlreadyUsedException();
        }
        if (repository.findByNormalizedDisplayName(normalizedDisplayName).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
        PteSection section = parseSection(request.section());
        TaskRuntimeContractDescriptor contract = contractResolver.resolveActive(
                request.screenKey(), request.contractVersion());

        QuestionType definition = new QuestionType();
        definition.setCode(taskTypeKey);
        definition.setTaskTypeKey(taskTypeKey);
        definition.setDisplayName(TaskTypeKeyNormalizer.normalizeDisplayLabel(request.displayName()));
        definition.setNormalizedDisplayName(normalizedDisplayName);
        definition.setShortName(request.shortName().trim());
        definition.setInstruction(request.instruction().trim());
        definition.setSection(section);
        definition.setScored(contract.scoringEnabled());
        boolean administratorOverride = caller != null && caller.hasRole(SecurityRoles.PLATFORM_ADMIN);
        definition.setActive(administratorOverride && Boolean.TRUE.equals(request.active()));
        definition.setDisplayOrder(request.displayOrder());
        definition.setAuthorUserPublicId(caller == null ? null : caller.userId());
        definition.setLifecycleStatus(administratorOverride && Boolean.TRUE.equals(request.active())
                ? "ACTIVE" : "DRAFT");
        applyRuntimeContract(definition, contract);
        try {
            QuestionTypeResponse response = QuestionTypeMapper.toResponse(repository.save(definition), contract, false);
            if (observability != null) {
                observability.dynamicTaskTypeCreated();
            }
            audit(caller, ItembankConstants.TASK_TYPE_CREATED, response.publicId(),
                    "Created task type " + taskTypeKey);
            return response;
        } catch (DataIntegrityViolationException ex) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeKeyAlreadyUsedException();
        }
    }

    /**
     * Update metadata and optionally the runtime contract for a custom task type.
     */
    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateTaskTypeRequest request, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireDraftModification(caller, definition);
        if (caller != null && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                && "ACTIVE".equals(definition.getLifecycleStatus())) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
        String taskTypeKey = definition.getTaskTypeKey() == null ? definition.getCode() : definition.getTaskTypeKey();
        String normalizedDisplayName = normalizeDisplayNameForApi(request.displayName());
        if (repository.findByNormalizedDisplayName(normalizedDisplayName)
                .filter(existing -> !java.util.Objects.equals(existing.getPublicId(), publicId)).isPresent()) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
        boolean standardType = TaskTypeCodeCompatibility.isStandard(taskTypeKey);
        if (standardType) {
            StandardTaskTypeRuntimeGuard.rejectChange(definition, request);
        }
        // Unchanged runtime values on a standard type are a no-op, not a rebinding.
        boolean runtimeChange = !standardType && (request.screenKey() != null
                || request.contractVersion() != null || request.section() != null);
        if (runtimeChange && isRuntimeLocked(definition)) {
            auditRuntimeLockConflict(caller, publicId, taskTypeKey);
            throw new TaskTypeRuntimeLockedException(taskTypeKey);
        }
        if (runtimeChange && (request.screenKey() == null || request.contractVersion() == null
                || request.section() == null || request.section().isBlank())) {
            throw new TaskTypeCapabilityNotFoundException();
        }
        definition.setDisplayName(TaskTypeKeyNormalizer.normalizeDisplayLabel(request.displayName()));
        definition.setNormalizedDisplayName(normalizedDisplayName);
        definition.setShortName(request.shortName().trim());
        if (request.instruction() != null) {
            definition.setInstruction(request.instruction().trim());
        }
        definition.setDisplayOrder(request.displayOrder());
        boolean administratorOverride = caller != null && caller.hasRole(SecurityRoles.PLATFORM_ADMIN);
        definition.setActive(administratorOverride && Boolean.TRUE.equals(request.active()));
        definition.setLifecycleStatus(administratorOverride && Boolean.TRUE.equals(request.active())
                ? "ACTIVE" : "DRAFT");
        if (runtimeChange) {
            PteSection section = parseSection(request.section());
            TaskRuntimeContractDescriptor contract = contractResolver.resolveActive(
                    request.screenKey(), request.contractVersion());
            definition.setSection(section);
            definition.setScored(contract.scoringEnabled());
            applyRuntimeContract(definition, contract);
        }
        try {
            QuestionTypeResponse response = QuestionTypeMapper.toResponse(repository.save(definition),
                    contractResolver.resolveDynamic(definition), isRuntimeLocked(definition));
            audit(caller, ItembankConstants.TASK_TYPE_UPDATED, publicId,
                    "Updated task type metadata " + taskTypeKey);
            if (runtimeChange) {
                audit(caller, ItembankConstants.TASK_TYPE_RUNTIME_UPDATED, publicId,
                        "Updated runtime contract for task type " + taskTypeKey);
            }
            return response;
        } catch (DataIntegrityViolationException ex) {
            markDuplicate(caller, taskTypeKey);
            throw new TaskTypeDisplayNameAlreadyUsedException();
        }
    }

    private void applyRuntimeContract(QuestionType definition, TaskRuntimeContractDescriptor contract) {
        definition.setScreenKey(contract.screenKey());
        definition.setRuntimeProfileKey(contract.profileKey());
        definition.setRuntimeProfileVersion(contract.profileVersion());
        definition.setRuntimeBehaviorKey(contract.behaviorKey());
        definition.setRuntimeRendererKey(contract.rendererKey());
        definition.setRuntimeAnswerSchemaVersion(contract.answerSchemaVersion());
        definition.setRuntimeScoringProfileKey(contract.scoringProfileKey());
        definition.setRuntimeScoringProfileVersion(contract.scoringProfileVersion());
        definition.setRuntimeScoringMode(contract.scoringMode());
        definition.setRuntimeRequiredCapabilities(contract.requiredClientCapabilities().toArray(String[]::new));
        definition.setRuntimeMinAppVersion(contract.minSupportedAppVersion());
        definition.setAuthoringContractKey(contract.authoringContractKey());
        definition.setAuthoringContractVersion(contract.authoringContractVersion());
        TaskAuthoringRequirements requirements = contract.authoringRequirements();
        definition.setRequiresAudioPrompt(requirements.requiresAudioPrompt());
        definition.setRequiresImagePrompt(requirements.requiresImagePrompt());
        definition.setRequiresPromptText(requirements.requiresPromptText());
        definition.setRequiresOptions(requirements.requiresOptions());
        definition.setRequiresCorrectAnswer(requirements.requiresCorrectAnswer());
        definition.setRequiresWordCount(requirements.requiresWordCount());
        definition.setRequiresSingleCorrectOption(requirements.requiresSingleCorrectOption());
        definition.setUsesOptionOrderAsCorrectPosition(requirements.usesOptionOrderAsCorrectPosition());
        definition.setRequiresReferenceAnswer(requirements.requiresReferenceAnswer());
    }

    private QuestionType findByPublicIdOrThrow(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(com.pte.itembank.internal.exception.tasktype.QuestionTypeNotFoundException::new);
    }

    private boolean isRuntimeLocked(QuestionType definition) {
        return definition.getRuntimeLockedAt() != null;
    }

    private void requireDraftWrite(CurrentUser caller) {
        if (caller != null && !SecurityPolicy.canCreateAcademicDraft(caller)) {
            deny(caller, null, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireDraftModification(CurrentUser caller, QuestionType definition) {
        if (caller != null && !SecurityPolicy.canModifyAcademicDraft(caller, definition.getAuthorUserPublicId())) {
            deny(caller, definition.getPublicId(), ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void deny(CurrentUser caller, UUID aggregateId, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                    aggregateId == null ? "unknown" : aggregateId.toString(),
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new org.springframework.security.access.AccessDeniedException(message);
    }

    private void auditRuntimeLockConflict(CurrentUser caller, UUID publicId, String taskTypeKey) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                    String.valueOf(publicId), ItembankConstants.TASK_TYPE_RUNTIME_LOCK_CONFLICT,
                    "Runtime fields are locked for task type " + taskTypeKey);
        }
    }

    private void markDuplicate(CurrentUser caller, String taskTypeKey) {
        if (observability != null) {
            observability.duplicateTaskTypeRejected();
        }
        audit(caller, ItembankConstants.TASK_TYPE_DUPLICATE_REJECTED, taskTypeKey,
                "A task type key or display name was already used");
    }

    private void audit(CurrentUser caller, String action, Object aggregateId, String summary) {
        if (auditLogService == null || caller == null) {
            return;
        }
        auditLogService.record(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                String.valueOf(aggregateId), action, summary.length() <= 500 ? summary : summary.substring(0, 500));
    }

    private String normalizeKeyForApi(String rawKey) {
        try {
            return rawKey == null ? null : TaskTypeKeyNormalizer.normalizeKey(rawKey);
        } catch (RuntimeException ex) {
            throw new TaskTypeKeyInvalidException();
        }
    }

    private String normalizeDisplayNameForApi(String rawDisplayName) {
        try {
            return rawDisplayName == null ? null : TaskTypeKeyNormalizer.normalizeDisplayName(rawDisplayName);
        } catch (RuntimeException ex) {
            throw new TaskTypeDisplayNameInvalidException();
        }
    }

    private PteSection parseSection(String section) {
        try {
            return PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new InvalidQuestionTypeException();
        }
    }
}
