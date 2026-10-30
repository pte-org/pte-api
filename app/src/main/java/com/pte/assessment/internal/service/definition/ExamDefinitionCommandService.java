package com.pte.assessment.internal.service.definition;

import com.pte.itembank.TaskTypeObservability;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.dto.request.CreateExamDefinitionRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.constant.SharedConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.SecurityPolicy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Handles exam definition creation and modification commands: drafting, cloning, item replacement, and deletion.
 */
class ExamDefinitionCommandService {

    private final ExamDefinitionRepository repository;
    private final ExamDefinitionValidator validator;
    private final ExamDefinitionItemMapper itemMapper;
    private final AuditLogService auditLogService;
    private final TaskTypeObservability observability;

    ExamDefinitionCommandService(ExamDefinitionRepository repository, ExamDefinitionValidator validator,
            ExamDefinitionItemMapper itemMapper, AuditLogService auditLogService,
            TaskTypeObservability observability) {
        this.observability = observability;
        this.repository = repository;
        this.validator = validator;
        this.itemMapper = itemMapper;
        this.auditLogService = auditLogService;
    }

    /** Creates an empty DRAFT in the next version of a template code family. */
    ExamDefinitionResponse createDraft(CreateExamDefinitionRequest request, CurrentUser caller) {
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

    /** Copies every item of source into a new DRAFT one version ahead, same code. */
    ExamDefinitionResponse cloneToDraft(ExamDefinition source, CurrentUser caller) {
        requireDraftCreate(caller);
        repository.findAllByCodeForUpdate(source.getCode());
        int nextVersion = repository.findMaxVersionByCode(source.getCode()) + 1;

        ExamDefinition draft = new ExamDefinition();
        draft.setCode(source.getCode());
        draft.setVersion(nextVersion);
        draft.setName(source.getName());
        draft.setStatus(ExamDefinitionStatus.DRAFT);
        draft.setTemplatePolicy(source.getTemplatePolicy());
        draft.setAuthorUserPublicId(caller == null ? null : caller.userId());
        source.getItems().forEach(item -> draft.addItem(itemMapper.copyItem(item)));

        ExamDefinition saved = saveOrTranslateConflict(draft);
        audit(caller, ExamDefinitionConstants.AUDIT_CLONED, saved,
                "Cloned from version " + source.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    /** Replaces a DRAFT's name and full item list. */
    ExamDefinitionResponse replaceItems(ExamDefinition template, ReplaceExamDefinitionItemsRequest request,
            CurrentUser caller) {
        requireDraft(template);
        requireDraftModification(caller, template);
        try {
            template.setName(request.name());
            if (request.templatePolicy() != null) {
                template.setTemplatePolicy(parsePolicy(request.templatePolicy()));
            }
            template.getItems().clear();
            request.items().forEach(itemRequest -> template.addItem(itemMapper.toEntity(itemRequest)));
            if (!isCustom(template)) {
                ExamDefinitionActivationValidator.validateSkillWeightTotals(template);
            }
            validator.validateCatalog(template, false);
            ExamDefinition saved = repository.save(template);
            audit(caller, ExamDefinitionConstants.AUDIT_UPDATED, saved,
                    "Updated draft version " + saved.getVersion());
            return ExamDefinitionMapper.toResponse(saved);
        } catch (ExamDefinitionValidationException ex) {
            auditValidationFailure(caller, template, ex);
            throw ex;
        }
    }

    /** Deletes a DRAFT; ACTIVE and RETIRED versions remain immutable. */
    void deleteDraft(ExamDefinition template, CurrentUser caller) {
        requireDraft(template);
        requireDraftModification(caller, template);
        audit(caller, ExamDefinitionConstants.AUDIT_DELETED, template, "Deleted draft version " + template.getVersion());
        repository.delete(template);
    }

    private void requireDraft(ExamDefinition template) {
        if (template.getStatus() != ExamDefinitionStatus.DRAFT) {
            throw new com.pte.assessment.internal.exception.ExamDefinitionNotDraftException();
        }
    }

    void requireDraftCreate(CurrentUser caller) {
        if (caller != null && !SecurityPolicy.canCreateAcademicDraft(caller)) {
            deny(caller, null, ExamDefinitionConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private void requireDraftModification(CurrentUser caller, ExamDefinition template) {
        if (caller != null && !SecurityPolicy.canModifyAcademicDraft(caller, template.getAuthorUserPublicId())) {
            deny(caller, template, ExamDefinitionConstants.ACADEMIC_DRAFT_WRITE_REQUIRED);
        }
    }

    private boolean isCustom(ExamDefinition template) {
        return template.getTemplatePolicy() == com.pte.assessment.domain.enums.TemplatePolicy.CUSTOM;
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

    private com.pte.assessment.domain.enums.TemplatePolicy parsePolicy(String raw) {
        if (raw == null || raw.isBlank()) {
            return com.pte.assessment.domain.enums.TemplatePolicy.STANDARD_PTE;
        }
        try {
            return com.pte.assessment.domain.enums.TemplatePolicy.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.UNKNOWN_TEMPLATE_POLICY + raw);
        }
    }
}
