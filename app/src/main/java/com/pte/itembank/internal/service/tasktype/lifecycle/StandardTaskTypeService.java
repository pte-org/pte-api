package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.domain.enums.PteSection;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.dto.request.CreateQuestionTypeRequest;
import com.pte.itembank.dto.request.UpdateQuestionTypeRequest;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.tasktype.InvalidQuestionTypeException;
import com.pte.itembank.internal.exception.tasktype.QuestionTypeCodeAlreadyUsedException;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.tasktype.RuntimeProfileResolver;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.itembank.TaskTypeKeyNormalizer;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityRoles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

/**
 * Manages the lifecycle of standard, enum-backed PTE question types
 * (create, update, soft-delete).
 */
@Service
public class StandardTaskTypeService {

    private final QuestionTypeRepository repository;
    private final RuntimeProfileResolver profileResolver;
    private final AuditLogService auditLogService;

    public StandardTaskTypeService(
            QuestionTypeRepository repository,
            RuntimeProfileResolver profileResolver,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.profileResolver = profileResolver;
        this.auditLogService = auditLogService;
    }

    /**
     * Create or restore a standard PTE task type.
     */
    @Transactional
    public QuestionTypeResponse create(CreateQuestionTypeRequest request, CurrentUser caller) {
        requireDraftWrite(caller);
        String code = TaskTypeCodeCompatibility.requireCanonicalCode(request.code());
        PteTaskType taskType = TaskTypeCodeCompatibility.parse(code);
        validateSection(taskType, request.section());

        QuestionType definition = repository.findByCode(code)
                .map(existing -> {
                    if (!existing.isDeleted()) {
                        throw new QuestionTypeCodeAlreadyUsedException();
                    }
                    return existing;
                })
                .orElseGet(QuestionType::new);

        definition.setDeleted(false);
        definition.setCode(code);
        definition.setTaskTypeKey(code);
        definition.setDisplayName(request.displayName().trim());
        definition.setNormalizedDisplayName(TaskTypeKeyNormalizer.normalizeDisplayName(request.displayName()));
        definition.setShortName(request.shortName().trim());
        definition.setSection(taskType.getSection());
        definition.setScored(taskType.isScored());
        definition.setActive(request.active());
        definition.setDisplayOrder(request.displayOrder());
        definition.setAuthorUserPublicId(caller == null ? null : caller.userId());
        if (caller != null && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            definition.setActive(false);
            definition.setLifecycleStatus("DRAFT");
        } else {
            definition.setLifecycleStatus(Boolean.TRUE.equals(request.active()) ? "ACTIVE" : "INACTIVE");
        }
        applyCanonicalRequirements(definition, taskType);

        definition = repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_CREATED, definition.getPublicId(),
                "Created standard task type " + code);
        return QuestionTypeMapper.toResponse(definition, profileResolver.resolveProfile(definition.getCode()));
    }

    /**
     * Update a standard task type definition.
     */
    @Transactional
    public QuestionTypeResponse update(UUID publicId, UpdateQuestionTypeRequest request, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireDraftModification(caller, definition);
        if (caller != null && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)
                && "ACTIVE".equals(definition.getLifecycleStatus())) {
            deny(caller, publicId, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
        definition.setDisplayName(request.displayName().trim());
        definition.setShortName(request.shortName().trim());
        definition.setDisplayOrder(request.displayOrder());
        definition.setActive(request.active());
        if (caller != null && !caller.hasRole(SecurityRoles.PLATFORM_ADMIN)) {
            definition.setActive(false);
            definition.setLifecycleStatus("DRAFT");
        }
        applyCanonicalRequirements(definition, TaskTypeCodeCompatibility.parse(definition.getCode()));
        definition = repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_UPDATED, publicId,
                "Updated standard task type " + definition.getCode());
        return QuestionTypeMapper.toResponse(definition, profileResolver.resolveProfile(definition.getCode()));
    }

    /**
     * Soft-delete a standard type (code remains stable for question FK).
     */
    @Transactional
    public void delete(UUID publicId, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireGovernance(caller, definition.getAuthorUserPublicId());
        definition.setActive(false);
        definition.setDeleted(true);
        repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_RETIRED, publicId,
                "Deleted standard task type " + definition.getCode());
    }

    private void applyCanonicalRequirements(QuestionType definition, PteTaskType taskType) {
        definition.setRequiresAudioPrompt(taskType.requiresAudioPrompt());
        definition.setRequiresImagePrompt(taskType.requiresImagePrompt());
        definition.setRequiresPromptText(taskType.requiresPromptText());
        definition.setRequiresOptions(taskType.requiresOptions());
        definition.setRequiresCorrectAnswer(taskType.requiresCorrectAnswer());
        definition.setRequiresWordCount(taskType.requiresWordCount());
        definition.setRequiresSingleCorrectOption(
                taskType == PteTaskType.MC_READING_SINGLE || taskType == PteTaskType.MC_LISTENING_SINGLE);
        definition.setUsesOptionOrderAsCorrectPosition(taskType == PteTaskType.RE_ORDER_PARAGRAPHS);
        definition.setRequiresReferenceAnswer(taskType.requiresReferenceAnswer());
    }

    private void validateSection(PteTaskType taskType, String section) {
        if (section == null) {
            throw new InvalidQuestionTypeException();
        }
        final PteSection requestedSection;
        try {
            requestedSection = PteSection.valueOf(section.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidQuestionTypeException();
        }
        if (taskType.getSection() != requestedSection) {
            throw new InvalidQuestionTypeException();
        }
    }

    private QuestionType findByPublicIdOrThrow(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(com.pte.itembank.internal.exception.tasktype.QuestionTypeNotFoundException::new);
    }

    private void requireDraftWrite(CurrentUser caller) {
        if (caller != null && !com.pte.shared.security.SecurityPolicy.canCreateAcademicDraft(caller)) {
            deny(caller, null, ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireDraftModification(CurrentUser caller, QuestionType definition) {
        if (caller != null && !com.pte.shared.security.SecurityPolicy.canModifyAcademicDraft(caller, definition.getAuthorUserPublicId())) {
            deny(caller, definition.getPublicId(), ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireGovernance(CurrentUser caller, UUID authorUserPublicId) {
        if (caller == null) {
            return;
        }
        if (com.pte.shared.security.SecurityPolicy.canPublishAcademic(caller, authorUserPublicId)) {
            return;
        }
        if (caller.hasRole(SecurityRoles.ACADEMIC_MANAGER)
                && authorUserPublicId != null && authorUserPublicId.equals(caller.userId())) {
            deny(caller, null, ItembankConstants.ACADEMIC_SELF_APPROVAL_FORBIDDEN);
        }
        deny(caller, null, ItembankConstants.ACADEMIC_PUBLISH_REQUIRED);
    }

    private void deny(CurrentUser caller, UUID aggregateId, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                    aggregateId == null ? "unknown" : aggregateId.toString(),
                    com.pte.shared.constant.SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new org.springframework.security.access.AccessDeniedException(message);
    }

    private void audit(CurrentUser caller, String action, Object aggregateId, String summary) {
        if (auditLogService == null || caller == null) {
            return;
        }
        auditLogService.record(caller, ItembankConstants.TASK_TYPE_AUDIT_AGGREGATE,
                String.valueOf(aggregateId), action, summary.length() <= 500 ? summary : summary.substring(0, 500));
    }
}
