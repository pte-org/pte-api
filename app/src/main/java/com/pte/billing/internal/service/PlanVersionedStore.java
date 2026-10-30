package com.pte.billing.internal.service;

import com.pte.billing.domain.ServicePlan;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.exception.plan.PlanLifecycleException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.exception.plan.PlanValidationException;
import com.pte.billing.internal.mapper.PlanMapper;
import com.pte.billing.internal.repository.ServicePlanRepository;
import org.springframework.dao.OptimisticLockingFailureException;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Loads, locks and saves plans under optimistic versioning, and renders them with their usage flags. */
class PlanVersionedStore {

    private final ServicePlanRepository planRepository;
    private final Clock clock;

    PlanVersionedStore(ServicePlanRepository planRepository, Clock clock) {
        this.planRepository = planRepository;
        this.clock = clock;
    }

    ServicePlan find(UUID publicId) {
        return planRepository.findByPublicId(publicId)
                .filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    ServicePlan findForUpdate(UUID publicId) {
        return planRepository.findByPublicIdForUpdate(publicId)
                .filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    void requireExpectedVersion(ServicePlan plan, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new PlanValidationException(BillingConstants.PLAN_VERSION_INVALID);
        }
        if (expectedVersion.longValue() != plan.getVersion()) {
            throw versionConflict();
        }
    }

    ServicePlan persist(ServicePlan plan) {
        try {
            return planRepository.saveAndFlush(plan);
        } catch (OptimisticLockingFailureException ex) {
            throw versionConflict();
        }
    }

    private PlanLifecycleException versionConflict() {
        return new PlanLifecycleException(BillingConstants.PLAN_VERSION_CONFLICT,
                BillingConstants.PLAN_VERSION_CONFLICT_MESSAGE);
    }

    void requireNoOutstandingCodes(ServicePlan plan) {
        if (planRepository.findOutstandingCodePlanIds(List.of(plan.getPublicId()), clock.instant())
                .contains(plan.getPublicId())) {
            throw new PlanLifecycleException(BillingConstants.PLAN_HAS_OUTSTANDING_CODES,
                    BillingConstants.PLAN_HAS_OUTSTANDING_CODES_MESSAGE);
        }
    }

    PlanResponse response(ServicePlan plan) {
        return responses(List.of(plan)).getFirst();
    }

    List<PlanResponse> responses(List<ServicePlan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = plans.stream().map(ServicePlan::getPublicId).toList();
        Set<UUID> referenced = planRepository.findReferencedPlanIds(ids);
        Set<UUID> outstanding = planRepository.findOutstandingCodePlanIds(ids, clock.instant());
        return plans.stream()
                .map(plan -> PlanMapper.toResponse(plan, referenced.contains(plan.getPublicId()),
                        outstanding.contains(plan.getPublicId())))
                .toList();
    }
}
