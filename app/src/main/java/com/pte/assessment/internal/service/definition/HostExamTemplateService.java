package com.pte.assessment.internal.service.definition;

import com.pte.assessment.ExamDefinitionService;
import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.domain.enums.ExamDefinitionStatus;
import com.pte.assessment.domain.enums.TemplatePolicy;
import com.pte.assessment.dto.request.CreateHostExamTemplateRequest;
import com.pte.assessment.dto.request.NormalizeSkillWeightsRequest;
import com.pte.assessment.dto.request.ReplaceExamDefinitionItemsRequest;
import com.pte.assessment.dto.response.ExamDefinitionFeasibilityResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionConcurrentModificationException;
import com.pte.assessment.internal.exception.ExamDefinitionNotDraftException;
import com.pte.assessment.internal.exception.ExamDefinitionNotFoundException;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import com.pte.assessment.internal.exception.NoActiveExamDefinitionException;
import com.pte.assessment.internal.mapper.ExamDefinitionMapper;
import com.pte.assessment.internal.repository.ExamDefinitionRepository;
import com.pte.itembank.ItembankService;
import com.pte.itembank.QuestionTypeService;
import com.pte.itembank.TaskRuntimeProfileService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Templates a host builds for its own tenant. A host starts from a copy of the platform default template and
 * may only remove task types from it; it can never see or use another tenant's template. Host templates need
 * no platform approval — they go live as soon as they pass the same readiness checks as any template — and any
 * number of them can be ACTIVE at once (the "single ACTIVE" rule belongs to the platform default only).
 */
@Service
public class HostExamTemplateService {

    private static final String CODE_PREFIX = "HOST_";
    /** Matches the limit on {@code CreateHostExamTemplateRequest.name}. */
    private static final int MAX_NAME_LENGTH = 255;

    private final ExamDefinitionRepository repository;
    private final ExamDefinitionService examDefinitionService;
    private final QuestionTypeService questionTypeService;
    private final HostExamTemplateAudit audit;
    private final Clock clock;
    private final ExamDefinitionValidator validator;
    private final ExamDefinitionItemMapper itemMapper;

    @Autowired
    public HostExamTemplateService(ExamDefinitionRepository repository, QuestionTypeService questionTypeService,
            TaskRuntimeProfileService runtimeProfileService, ItembankService itembankService,
            AuditLogService auditLogService, Clock clock, ExamDefinitionService examDefinitionService) {
        this.repository = repository;
        this.examDefinitionService = examDefinitionService;
        this.questionTypeService = questionTypeService;
        this.audit = new HostExamTemplateAudit(auditLogService);
        this.clock = clock;
        this.validator = new ExamDefinitionValidator(questionTypeService, runtimeProfileService, itembankService);
        this.itemMapper = new ExamDefinitionItemMapper(validator);
    }

    @Transactional(readOnly = true)
    public List<ExamDefinitionResponse> list(CurrentUser caller) {
        return repository.findAllByTenantIdOrderByCodeAscVersionDesc(requireTenant(caller)).stream()
                .map(ExamDefinitionMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ExamDefinitionResponse get(UUID publicId, CurrentUser caller) {
        return ExamDefinitionMapper.toResponse(findOwned(publicId, caller));
    }

    /** Per task type: how many published questions exist against how many the template needs. */
    @Transactional(readOnly = true)
    public ExamDefinitionFeasibilityResponse feasibility(UUID publicId, CurrentUser caller) {
        findOwned(publicId, caller);
        return examDefinitionService.getTemplateFeasibility(publicId);
    }

    /** Suggests how to rebalance skill columns for the host's unsaved rows; computes only, writes nothing. */
    public SkillWeightSuggestionResponse suggestSkillWeights(NormalizeSkillWeightsRequest request,
            CurrentUser caller) {
        requireTenant(caller);
        return SkillWeightNormalizer.suggest(request.items());
    }

    /** New template that starts as a copy of the ACTIVE platform default. */
    @Transactional
    public ExamDefinitionResponse createFromDefault(CreateHostExamTemplateRequest request, CurrentUser caller) {
        UUID tenantId = requireTenant(caller);
        ExamDefinition base = repository.findWithItemsByStatusAndTenantIdIsNull(ExamDefinitionStatus.ACTIVE)
                .orElseThrow(NoActiveExamDefinitionException::new);

        ExamDefinition draft = newDraft(tenantId, base, generateCode(tenantId), 1, request.name().trim(), caller);
        base.getItems().forEach(item -> draft.addItem(itemMapper.copyItem(item)));
        ExamDefinition saved = save(draft);
        audit.record(caller, ExamDefinitionConstants.AUDIT_CREATED, saved, "Created host template from default version "
                + base.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    /**
     * Copies one of the host's own templates into an independent DRAFT with its own code, so activating the copy
     * never retires the original.
     */
    @Transactional
    public ExamDefinitionResponse cloneToDraft(UUID publicId, CurrentUser caller) {
        ExamDefinition source = findOwned(publicId, caller);

        ExamDefinition draft = new ExamDefinition();
        draft.setCode(generateCode(source.getTenantId()));
        draft.setVersion(1);
        draft.setName(copyName(source.getName()));
        draft.setStatus(ExamDefinitionStatus.DRAFT);
        draft.setTemplatePolicy(TemplatePolicy.CUSTOM);
        draft.setTenantId(source.getTenantId());
        draft.setBaseDefinitionPublicId(source.getBaseDefinitionPublicId());
        draft.setAuthorUserPublicId(caller.userId());
        source.getItems().forEach(item -> draft.addItem(itemMapper.copyItem(item)));
        ExamDefinition saved = save(draft);
        audit.record(caller, ExamDefinitionConstants.AUDIT_CLONED, saved, "Cloned from template '" + source.getName()
                + "' version " + source.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    private static String copyName(String sourceName) {
        String suffix = " (copy)";
        String base = sourceName.length() + suffix.length() > MAX_NAME_LENGTH
                ? sourceName.substring(0, MAX_NAME_LENGTH - suffix.length())
                : sourceName;
        return base + suffix;
    }

    /** Replaces a DRAFT's name and items; every task type must exist in the template's base default. */
    @Transactional
    public ExamDefinitionResponse replaceItems(UUID publicId, ReplaceExamDefinitionItemsRequest request,
            CurrentUser caller) {
        ExamDefinition template = findOwned(publicId, caller);
        requireDraft(template);
        try {
            template.setName(request.name().trim());
            template.getItems().clear();
            request.items().forEach(item -> template.addItem(itemMapper.toEntity(item)));
            validator.validateCatalog(template, false);
            validator.validateWithinBase(template, requireBase(template));
        } catch (ExamDefinitionValidationException ex) {
            audit.failure(caller, template, ex);
            throw ex;
        }
        ExamDefinition saved = repository.save(template);
        audit.record(caller, ExamDefinitionConstants.AUDIT_UPDATED, saved, "Updated draft version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findOwned(publicId, caller);
        requireDraft(template);
        audit.record(caller, ExamDefinitionConstants.AUDIT_DELETED, template, "Deleted draft version " + template.getVersion());
        repository.delete(template);
    }

    /**
     * Makes a DRAFT (or a RETIRED version) usable for exams. Other ACTIVE versions of the same template family are
     * retired; the host's other templates stay as they are.
     */
    @Transactional
    public ExamDefinitionResponse activate(UUID publicId, CurrentUser caller) {
        ExamDefinition target = findOwned(publicId, caller);
        if (target.getStatus() != ExamDefinitionStatus.DRAFT && target.getStatus() != ExamDefinitionStatus.RETIRED) {
            throw new ExamDefinitionNotDraftException();
        }
        try {
            validator.validateCatalog(target, true);
            validator.validateWithinBase(target, requireBase(target));
            ExamDefinitionActivationValidator.validate(target);
            validator.validateQuestionBank(target);
        } catch (ExamDefinitionValidationException ex) {
            audit.failure(caller, target, ex);
            throw ex;
        }

        repository.findAllByCodeForUpdate(target.getCode());
        repository.findAllWithItemsByTenantIdAndCodeAndStatus(target.getTenantId(), target.getCode(),
                        ExamDefinitionStatus.ACTIVE).stream()
                .filter(active -> !active.getPublicId().equals(target.getPublicId()))
                .forEach(active -> {
                    active.setStatus(ExamDefinitionStatus.RETIRED);
                    repository.saveAndFlush(active);
                    audit.record(caller, ExamDefinitionConstants.AUDIT_RETIRED, active, "Retired version "
                            + active.getVersion() + " after activating version " + target.getVersion());
                });
        target.setStatus(ExamDefinitionStatus.ACTIVE);
        ExamDefinition saved = save(target);
        if (questionTypeService != null) {
            questionTypeService.recordPublishedUse(
                    saved.getItems().stream().map(this::logicalTaskKey).distinct().toList(), clock.instant());
        }
        audit.record(caller, ExamDefinitionConstants.AUDIT_ACTIVATED, saved, "Activated version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    /** Stops offering an ACTIVE template for new exams; exams already built from it keep working. */
    @Transactional
    public ExamDefinitionResponse retire(UUID publicId, CurrentUser caller) {
        ExamDefinition template = findOwned(publicId, caller);
        if (template.getStatus() != ExamDefinitionStatus.ACTIVE) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.HOST_TEMPLATE_NOT_ACTIVE);
        }
        template.setStatus(ExamDefinitionStatus.RETIRED);
        ExamDefinition saved = save(template);
        audit.record(caller, ExamDefinitionConstants.AUDIT_RETIRED, saved, "Retired version " + saved.getVersion());
        return ExamDefinitionMapper.toResponse(saved);
    }

    private ExamDefinition newDraft(UUID tenantId, ExamDefinition base, String code, int version, String name,
            CurrentUser caller) {
        ExamDefinition draft = new ExamDefinition();
        draft.setCode(code);
        draft.setVersion(version);
        draft.setName(name);
        draft.setStatus(ExamDefinitionStatus.DRAFT);
        draft.setTemplatePolicy(TemplatePolicy.CUSTOM);
        draft.setTenantId(tenantId);
        draft.setBaseDefinitionPublicId(base.getPublicId());
        draft.setAuthorUserPublicId(caller.userId());
        return draft;
    }

    /** A template of another tenant (or a platform default) is reported as not found, never as forbidden. */
    private ExamDefinition findOwned(UUID publicId, CurrentUser caller) {
        return repository.findWithItemsByPublicIdAndTenantId(publicId, requireTenant(caller))
                .orElseThrow(ExamDefinitionNotFoundException::new);
    }

    private ExamDefinition requireBase(ExamDefinition template) {
        UUID baseId = template.getBaseDefinitionPublicId();
        if (baseId == null) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.HOST_TEMPLATE_NO_BASE);
        }
        return repository.findWithItemsByPublicIdAndTenantIdIsNull(baseId)
                .orElseThrow(() -> new ExamDefinitionValidationException(ExamDefinitionConstants.HOST_TEMPLATE_NO_BASE));
    }

    private UUID requireTenant(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null) {
            throw new AccessDeniedException(ExamDefinitionConstants.HOST_TEMPLATE_TENANT_REQUIRED);
        }
        return caller.tenantId();
    }

    private void requireDraft(ExamDefinition template) {
        if (template.getStatus() != ExamDefinitionStatus.DRAFT) {
            throw new ExamDefinitionNotDraftException();
        }
    }

    private String generateCode(UUID tenantId) {
        String tenant = tenantId.toString().replace("-", "").substring(0, 8);
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return CODE_PREFIX + tenant + "_" + random;
    }

    private ExamDefinition save(ExamDefinition template) {
        try {
            return repository.save(template);
        } catch (DataIntegrityViolationException | OptimisticLockingFailureException ex) {
            throw new ExamDefinitionConcurrentModificationException();
        }
    }

    private String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? TaskTypeCodeCompatibility.normalizeForLookup(item.getTaskType())
                : TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.getTaskTypeKey());
    }
}
