package com.pte.itembank.internal.service.tasktype.lifecycle;

import com.pte.itembank.domain.QuestionType;
import com.pte.itembank.dto.response.QuestionTypeResponse;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.repository.QuestionTypeRepository;
import com.pte.itembank.internal.service.tasktype.RuntimeContractResolver;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import com.pte.shared.constant.SharedConstants;
import com.pte.itembank.internal.mapper.QuestionTypeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Manages task-type approval, submission, and retirement workflows.
 */
@Service
public class TaskTypeGovernanceService {

    private final QuestionTypeRepository repository;
    private final RuntimeContractResolver contractResolver;
    private final AuditLogService auditLogService;

    public TaskTypeGovernanceService(
            QuestionTypeRepository repository,
            RuntimeContractResolver contractResolver,
            AuditLogService auditLogService) {
        this.repository = repository;
        this.contractResolver = contractResolver;
        this.auditLogService = auditLogService;
    }

    /**
     * Submit a custom task type for academic approval.
     */
    @Transactional
    public QuestionTypeResponse submit(UUID publicId, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireDraftModification(caller, definition);
        if (!"DRAFT".equals(definition.getLifecycleStatus())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    ItembankConstants.TASK_TYPE_RUNTIME_LOCKED_MESSAGE);
        }
        definition.setActive(false);
        definition.setLifecycleStatus("PENDING_APPROVAL");
        QuestionType saved = repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_SUBMITTED, publicId,
                "Submitted task type for academic approval");
        return QuestionTypeMapper.toResponse(saved, contractResolver.resolveDynamic(saved), isRuntimeLocked(saved));
    }

    /**
     * Approve and activate a task type submission.
     */
    @Transactional
    public QuestionTypeResponse approve(UUID publicId, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireGovernance(caller, definition.getAuthorUserPublicId());
        if (!"PENDING_APPROVAL".equals(definition.getLifecycleStatus())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    ItembankConstants.TASK_TYPE_RUNTIME_LOCKED_MESSAGE);
        }
        definition.setActive(true);
        definition.setLifecycleStatus("ACTIVE");
        QuestionType saved = repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_APPROVED, publicId,
                "Approved and activated task type");
        audit(caller, ItembankConstants.TASK_TYPE_PUBLISHED, publicId,
                "Published task type runtime catalog entry");
        return QuestionTypeMapper.toResponse(saved, contractResolver.resolveDynamic(saved), isRuntimeLocked(saved));
    }

    /**
     * Retire an active custom task type.
     */
    @Transactional
    public void retire(UUID publicId, CurrentUser caller) {
        QuestionType definition = findByPublicIdOrThrow(publicId);
        requireGovernance(caller, definition.getAuthorUserPublicId());
        definition.setActive(false);
        definition.setLifecycleStatus("RETIRED");
        repository.save(definition);
        audit(caller, ItembankConstants.TASK_TYPE_RETIRED, publicId,
                "Retired task type " + (definition.getTaskTypeKey() == null ? definition.getCode()
                        : definition.getTaskTypeKey()));
    }

    private QuestionType findByPublicIdOrThrow(UUID publicId) {
        return repository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(com.pte.itembank.internal.exception.tasktype.QuestionTypeNotFoundException::new);
    }

    private boolean isRuntimeLocked(QuestionType definition) {
        return definition.getRuntimeLockedAt() != null;
    }

    private void requireDraftModification(CurrentUser caller, QuestionType definition) {
        if (caller != null && !SecurityPolicy.canModifyAcademicDraft(caller, definition.getAuthorUserPublicId())) {
            deny(caller, definition.getPublicId(), ItembankConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireGovernance(CurrentUser caller, UUID authorUserPublicId) {
        if (caller == null) {
            return;
        }
        if (SecurityPolicy.canPublishAcademic(caller, authorUserPublicId)) {
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
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
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
