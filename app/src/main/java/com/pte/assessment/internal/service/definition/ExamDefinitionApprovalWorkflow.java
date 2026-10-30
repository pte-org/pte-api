package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.assessment.dto.request.RejectExamDefinitionRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionNotDraftException;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskTypeObservability;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import com.pte.shared.security.SecurityRoles;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;

/**
 * Handles exam definition approval workflow: submission, review approval/rejection, and publication.
 * Maintains audit trails and validates authorization at each stage.
 */
class ExamDefinitionApprovalWorkflow {

    private final ExamDefinitionRepository repository;
    private final ExamDefinitionValidator validator;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;
    private final QuestionTypeService questionTypeService;
    private final Clock clock;

    ExamDefinitionApprovalWorkflow(ExamDefinitionRepository repository, ExamDefinitionValidator validator,
            AuditLogService auditLogService, TaskTypeObservability observability,
            QuestionTypeService questionTypeService, Clock clock) {
        this.repository = repository;
        this.validator = validator;
        this.auditLogService = auditLogService;
        this.observability = observability;
        this.questionTypeService = questionTypeService;
        this.clock = clock;
    }

    /** Author submits draft for admin review. */
    ExamDefinitionResponse submitApproval(ExamDefinition template, CurrentUser caller) {
        requireDraft(template);
        requireDraftModification(caller, template);
        try {
            validator.validateCatalog(template, true);
            ExamDefinitionActivationValidator.validate(template);
            validator.validateQuestionBank(template);
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

    /** Admin approves submitted template for publication. */
    ExamDefinitionResponse approve(ExamDefinition template, CurrentUser caller) {
        requireReview(caller, template);
        requirePendingApproval(template);
        try {
            validator.validateCatalog(template, true);
            ExamDefinitionActivationValidator.validate(template);
            validator.validateQuestionBank(template);
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

    /** Admin rejects submitted template, returns to draft with reason. */
    ExamDefinitionResponse reject(ExamDefinition template, RejectExamDefinitionRequest request, CurrentUser caller) {
        requireReview(caller, template);
        requirePendingApproval(template);
        template.setRejectionReason(request.reason().trim());
        template.setStatus(ExamDefinitionStatus.DRAFT);
        ExamDefinitionResponse response = ExamDefinitionMapper.toResponse(repository.save(template));
        audit(caller, ExamDefinitionConstants.AUDIT_REJECTED, template,
                "Returned version " + template.getVersion() + " to draft: " + template.getRejectionReason());
        return response;
    }

    /** Activates approved draft, retires previous active version. */
    ExamDefinitionResponse activate(ExamDefinition target, CurrentUser caller) {
        requireDraft(target);
        requirePublish(caller, target);
        try {
            validator.validateCatalog(target, true);
            ExamDefinitionActivationValidator.validate(target);
            validator.validateQuestionBank(target);
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
                    target.getItems().stream().map(this::logicalTaskKey).distinct().toList(), clock.instant());
            audit(caller, ExamDefinitionConstants.AUDIT_PUBLICATION_USAGE_RECORDED, target,
                    "Recorded runtime lock usage for " + target.getItems().stream()
                            .map(this::logicalTaskKey).distinct().sorted().toList());
        }
        audit(caller, ExamDefinitionConstants.AUDIT_ACTIVATED, saved,
                "Activated version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
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

    private void requireDraftModification(CurrentUser caller, ExamDefinition template) {
        if (caller != null && !SecurityPolicy.canModifyAcademicDraft(caller, template.getAuthorUserPublicId())) {
            deny(caller, template, ExamDefinitionConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void deny(CurrentUser caller, ExamDefinition template, String message) {
        if (auditLogService != null && caller != null) {
            auditLogService.recordFailure(caller, ExamDefinitionConstants.AUDIT_AGGREGATE_TYPE,
                    template == null ? "unknown" : String.valueOf(template.getPublicId()),
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, message);
        }
        throw new AccessDeniedException(message);
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
            throw new com.pte.assessment.internal.exception.ExamDefinitionConcurrentModificationException();
        }
    }

    private String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType())
                : TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.getTaskTypeKey());
    }
}
