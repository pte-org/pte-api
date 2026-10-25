package com.pte.billing.internal.service;

import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.plan.PlanRequest;
import com.pte.billing.internal.dto.request.plan.PlanTransitionRequest;
import com.pte.billing.internal.dto.request.plan.PlanUpdateRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.exception.plan.PlanLifecycleException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.exception.plan.PlanStateException;
import com.pte.billing.internal.exception.plan.PlanValidationException;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.constant.SharedConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Platform plan catalog and its explicit draft/active/archived lifecycle. */
@Service
public class PlanService {

    private final ServicePlanRepository planRepository;
    private final AuditLogService auditLogService;
    private final PlanVersionedStore store;

    @Autowired
    public PlanService(ServicePlanRepository planRepository, AuditLogService auditLogService, Clock clock) {
        this.planRepository = planRepository;
        this.auditLogService = auditLogService;
        this.store = new PlanVersionedStore(planRepository, clock);
    }

    /** Compatibility constructor for existing focused tests. */
    public PlanService(ServicePlanRepository planRepository) {
        this(planRepository, null, Clock.systemUTC());
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        return create(request, null);
    }

    @Transactional
    public PlanResponse create(PlanRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_DRAFT_WRITE);
        if (request == null) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_REQUIRED);
        }
        PlanType type = PlanDefinitionRules.parseType(request.type());
        PlanDefinitionRules.validate(request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());

        ServicePlan plan = new ServicePlan();
        PlanDefinitionRules.apply(plan, request.name(), request.description(), request.price(), request.currency(),
                type, request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        PlanResponse result = store.response(store.persist(plan));
        audit(caller, BillingConstants.AUDIT_PLAN_TRANSITION, plan.getPublicId(), BillingConstants.PLAN_CREATED_SUMMARY);
        return result;
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID publicId) {
        return get(publicId, null);
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_READ_ALL);
        return store.response(store.find(publicId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin() {
        return listForAdmin(null);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin(CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_READ_ALL);
        return store.responses(planRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(plan -> !plan.isDeleted())
                .toList());
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActive() {
        return store.responses(planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE).stream()
                .filter(plan -> !plan.isDeleted())
                .toList());
    }

    @Transactional
    public PlanResponse update(UUID publicId, PlanUpdateRequest request) {
        return update(publicId, request, null);
    }

    @Transactional
    public PlanResponse update(UUID publicId, PlanUpdateRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_DRAFT_WRITE);
        if (request == null) {
            throw new PlanValidationException(BillingConstants.PLAN_VERSION_REQUIRED);
        }
        ServicePlan plan = store.findForUpdate(publicId);
        store.requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ARCHIVED_NOT_EDITABLE);
        }

        PlanType type = PlanDefinitionRules.parseType(request.type());
        if (plan.getStatus() == PlanStatus.ACTIVE && type != plan.getType()) {
            throw new PlanLifecycleException(BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE,
                    BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE_MESSAGE);
        }
        PlanDefinitionRules.validate(request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        if (type != plan.getType() || !Objects.equals(request.durationDays(), plan.getDurationDays())
                || !Objects.equals(request.maxStudentsPerSession(), plan.getMaxStudentsPerSession())
                || !Objects.equals(request.extraStudentSlots(), plan.getExtraStudentSlots())) {
            store.requireNoOutstandingCodes(plan);
        }

        PlanDefinitionRules.apply(plan, request.name(), request.description(), request.price(), request.currency(),
                type, request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        PlanResponse result = store.response(store.persist(plan));
        audit(caller, BillingConstants.AUDIT_PLAN_TRANSITION, publicId, BillingConstants.PLAN_UPDATED_SUMMARY);
        return result;
    }

    @Transactional
    public PlanResponse activate(UUID publicId, PlanTransitionRequest request) {
        return activate(publicId, request, null);
    }

    @Transactional
    public PlanResponse activate(UUID publicId, PlanTransitionRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_PUBLISH);
        if (request == null) {
            throw new PlanValidationException(BillingConstants.PLAN_VERSION_REQUIRED);
        }
        ServicePlan plan = store.findForUpdate(publicId);
        store.requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanStateException(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE);
        }
        PlanDefinitionRules.validate(plan.getName(), plan.getDescription(), plan.getPrice(), plan.getCurrency(),
                plan.getType(), plan.getDurationDays(), plan.getMaxStudentsPerSession(), plan.getExtraStudentSlots());
        plan.activate();
        PlanResponse result = store.response(store.persist(plan));
        audit(caller, BillingConstants.AUDIT_PLAN_TRANSITION, publicId, BillingConstants.PLAN_ACTIVATED_SUMMARY);
        return result;
    }

    @Transactional
    public PlanResponse archive(UUID publicId, PlanTransitionRequest request) {
        return archive(publicId, request, null);
    }

    @Transactional
    public PlanResponse archive(UUID publicId, PlanTransitionRequest request, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_PUBLISH);
        if (request == null) {
            throw new PlanValidationException(BillingConstants.PLAN_VERSION_REQUIRED);
        }
        ServicePlan plan = store.findForUpdate(publicId);
        store.requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ALREADY_ARCHIVED);
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new PlanLifecycleException(BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE,
                    BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE_MESSAGE);
        }
        store.requireNoOutstandingCodes(plan);
        plan.archive();
        PlanResponse result = store.response(store.persist(plan));
        audit(caller, BillingConstants.AUDIT_PLAN_TRANSITION, publicId, BillingConstants.PLAN_ARCHIVED_SUMMARY);
        return result;
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_DRAFT_WRITE);
        ServicePlan plan = planRepository.findByPublicIdForUpdate(publicId)
                .orElseThrow(PlanNotFoundException::new);
        if (plan.isDeleted()) {
            return;
        }
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanLifecycleException(BillingConstants.PLAN_DELETE_DRAFT_ONLY,
                    BillingConstants.PLAN_DELETE_DRAFT_ONLY_MESSAGE);
        }
        if (planRepository.findReferencedPlanIds(List.of(publicId)).contains(publicId)) {
            throw new PlanLifecycleException(BillingConstants.PLAN_HAS_REFERENCES,
                    BillingConstants.PLAN_HAS_REFERENCES_MESSAGE);
        }
        plan.setDeleted(true);
        audit(caller, BillingConstants.PLAN_DRAFT_DELETED, publicId, BillingConstants.PLAN_DRAFT_DELETED_SUMMARY);
    }

    public static void validateExamBenefits(Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        PlanDefinitionRules.validateExamBenefits(durationDays, maxStudentsPerSession, extraStudentSlots);
    }

    private void require(CurrentUser caller, PlatformOperation operation) {
        if (caller == null) {
            return;
        }
        if (PlatformOperationPolicy.can(caller, operation)) {
            return;
        }
        if (auditLogService != null) {
            auditLogService.recordFailure(caller, BillingConstants.PLAN_AGGREGATE, "unknown",
                    SharedConstants.AUDIT_AUTHORIZATION_DENIED, operation.name());
        }
        throw new org.springframework.security.access.AccessDeniedException(SharedConstants.ACCESS_DENIED);
    }

    private void audit(CurrentUser caller, String action, UUID publicId, String summary) {
        if (auditLogService != null && caller != null && publicId != null) {
            auditLogService.record(caller, BillingConstants.PLAN_AGGREGATE, publicId.toString(), action, summary);
        }
    }
}
