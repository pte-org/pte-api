package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.itembank.ItembankService;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskRuntimeContractService;
import com.pte.itembank.TaskTypeObservability;
import com.pte.itembank.QuestionTypeService;
import com.pte.assessment.dto.request.CreateExamDefinitionRequest;
import com.pte.assessment.dto.request.RejectExamDefinitionRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
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
    private final Clock clock;
    private final ExamDefinitionValidator validator;
    private final ExamDefinitionItemMapper itemMapper;
    private final ExamDefinitionCommandService commandService;
    private final ExamDefinitionApprovalWorkflow approvalWorkflow;

    @Autowired
    public ExamDefinitionAdminService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService,
            TaskRuntimeProfileService runtimeProfileService, AuditLogService auditLogService,
            TaskRuntimeContractService runtimeContractService,
            TaskTypeObservability observability, ItembankService itembankService,
            Clock clock) {
        this.repository = repository;
        this.questionTypeService = questionTypeService;
        this.runtimeProfileService = runtimeProfileService;
        this.runtimeContractService = runtimeContractService;
        this.auditLogService = auditLogService;
        this.observability = observability;
        this.itembankService = itembankService;
        this.clock = clock;
        this.validator = new ExamDefinitionValidator(questionTypeService, runtimeProfileService, itembankService);
        this.itemMapper = new ExamDefinitionItemMapper(validator);
        this.commandService = new ExamDefinitionCommandService(repository, validator, itemMapper, auditLogService,
                observability);
        this.approvalWorkflow = new ExamDefinitionApprovalWorkflow(repository, validator, auditLogService,
                observability, questionTypeService, clock);
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
        return commandService.createDraft(request, caller);
    }

    /** Copies every item of {@code sourcePublicId} into a new DRAFT one version ahead, same {@code code}. */
    @Transactional
    public ExamDefinitionResponse cloneToDraft(UUID sourcePublicId) {
        return cloneToDraft(sourcePublicId, null);
    }

    @Transactional
    public ExamDefinitionResponse cloneToDraft(UUID sourcePublicId, CurrentUser caller) {
        commandService.requireDraftCreate(caller);
        ExamDefinition source = findByPublicId(sourcePublicId);
        return commandService.cloneToDraft(source, caller);
    }

    /**
     * Replaces a DRAFT's name + full item list — no partial/incremental item edits (FR-02).
     */
    @Transactional
    public ExamDefinitionResponse replaceItems(UUID draftPublicId, ReplaceExamDefinitionItemsRequest request) {
        return replaceItems(draftPublicId, request, null);
    }

    @Transactional
    public ExamDefinitionResponse replaceItems(UUID draftPublicId, ReplaceExamDefinitionItemsRequest request,
            CurrentUser caller) {
        ExamDefinition template = findByPublicId(draftPublicId);
        return commandService.replaceItems(template, request, caller);
    }

    /** Author submits a complete, structurally valid draft for admin review. */
    @Transactional
    public ExamDefinitionResponse submitApproval(UUID publicId) {
        return submitApproval(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse submitApproval(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        return approvalWorkflow.submitApproval(template, caller);
    }

    /** Admin approval returns the immutable review decision to an editable draft. */
    @Transactional
    public ExamDefinitionResponse approve(UUID publicId) {
        return approve(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse approve(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        return approvalWorkflow.approve(template, caller);
    }

    /** Admin rejection returns the template to DRAFT with an actionable reason. */
    @Transactional
    public ExamDefinitionResponse reject(UUID publicId, RejectExamDefinitionRequest request) {
        return reject(publicId, request, null);
    }

    @Transactional
    public ExamDefinitionResponse reject(UUID publicId, RejectExamDefinitionRequest request, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        return approvalWorkflow.reject(template, request, caller);
    }

    /** Deletes a DRAFT; ACTIVE and RETIRED versions remain immutable and auditable. */
    @Transactional
    public void deleteDraft(UUID publicId) {
        deleteDraft(publicId, null);
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findByPublicId(publicId);
        commandService.deleteDraft(template, caller);
    }

    /**
     * Activates approved draft: retires previous ACTIVE, transitions target to ACTIVE.
     */
    @Transactional
    public ExamDefinitionResponse activate(UUID publicId) {
        return activate(publicId, null);
    }

    @Transactional
    public ExamDefinitionResponse activate(UUID publicId, CurrentUser caller) {
        ExamDefinition target = findByPublicId(publicId);
        return approvalWorkflow.activate(target, caller);
    }

    private ExamDefinition findByPublicId(UUID publicId) {
        return repository.findWithItemsByPublicId(publicId).orElseThrow(ExamDefinitionNotFoundException::new);
    }
}
