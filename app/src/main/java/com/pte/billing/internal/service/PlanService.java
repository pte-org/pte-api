package com.pte.billing.internal.service;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.exception.PlanStateException;
import com.pte.billing.internal.exception.PlanValidationException;
import com.pte.billing.internal.exception.PlanLifecycleException;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import com.pte.billing.internal.mapper.PlanMapper;
import com.pte.billing.internal.repository.PlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Platform plan catalog and its explicit draft/active/archived lifecycle. */
@Service
public class PlanService {

    private final PlanRepository planRepository;
    private final AuditLogService auditLogService;

    public PlanService(PlanRepository planRepository, AuditLogService auditLogService) {
        this.planRepository = planRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        PlanType type = parseType(request.type());
        validate(request.name(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());

        Plan plan = new Plan();
        apply(plan, request, type);
        return response(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID publicId) {
        return response(find(publicId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin() {
        return responses(planRepository.findAllByOrderByCreatedAtDesc().stream().filter(p -> !p.isDeleted()).toList());
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActive() {
        return responses(planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE).stream().filter(p -> !p.isDeleted()).toList());
    }

    @Transactional
    public PlanResponse update(UUID publicId, PlanRequest request) {
        Plan plan = findForUpdate(publicId);
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ARCHIVED_NOT_EDITABLE);
        }

        PlanType type = parseType(request.type());
        if (plan.getStatus() == PlanStatus.ACTIVE && type != plan.getType()) {
            throw new PlanLifecycleException(BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE,
                    BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE_MESSAGE);
        }
        if (type != plan.getType() || !Objects.equals(request.durationDays(), plan.getDurationDays())
                || !Objects.equals(request.maxStudentsPerSession(), plan.getMaxStudentsPerSession())
                || !Objects.equals(request.extraStudentSlots(), plan.getExtraStudentSlots())) {
            requireNoOutstandingCodes(plan);
        }
        validate(request.name(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        apply(plan, request, type);
        return response(planRepository.save(plan));
    }

    @Transactional
    public PlanResponse activate(UUID publicId) {
        Plan plan = findForUpdate(publicId);
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanStateException(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE);
        }
        plan.activate();
        return response(plan);
    }

    @Transactional
    public PlanResponse archive(UUID publicId) {
        Plan plan = findForUpdate(publicId);
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ALREADY_ARCHIVED);
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new PlanLifecycleException(BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE,
                    BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE_MESSAGE);
        }
        requireNoOutstandingCodes(plan);
        plan.archive();
        return response(plan);
    }

    private Plan find(UUID publicId) {
        return planRepository.findByPublicId(publicId)
                .filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        if (!caller.isPlatformUser() || !caller.hasRole("PLATFORM_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException(com.pte.shared.constant.SharedConstants.ACCESS_DENIED);
        }
        Plan plan = planRepository.findByPublicIdForUpdate(publicId).orElseThrow(PlanNotFoundException::new);
        if (plan.isDeleted()) return;
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanLifecycleException(BillingConstants.PLAN_DELETE_DRAFT_ONLY, BillingConstants.PLAN_DELETE_DRAFT_ONLY_MESSAGE);
        }
        if (planRepository.findReferencedPlanIds(List.of(publicId)).contains(publicId)) {
            throw new PlanLifecycleException(BillingConstants.PLAN_HAS_REFERENCES, BillingConstants.PLAN_HAS_REFERENCES_MESSAGE);
        }
        plan.setDeleted(true);
        auditLogService.record(caller, BillingConstants.PLAN_AGGREGATE, publicId.toString(),
                BillingConstants.PLAN_DRAFT_DELETED, BillingConstants.PLAN_DRAFT_DELETED_SUMMARY);
    }

    private Plan findForUpdate(UUID publicId) {
        return planRepository.findByPublicIdForUpdate(publicId).filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    private void requireNoOutstandingCodes(Plan plan) {
        if (planRepository.findOutstandingCodePlanIds(List.of(plan.getPublicId()), Instant.now()).contains(plan.getPublicId())) {
            throw new PlanLifecycleException(BillingConstants.PLAN_HAS_OUTSTANDING_CODES, BillingConstants.PLAN_HAS_OUTSTANDING_CODES_MESSAGE);
        }
    }

    private PlanResponse response(Plan plan) {
        return responses(List.of(plan)).getFirst();
    }

    private List<PlanResponse> responses(List<Plan> plans) {
        if (plans.isEmpty()) return List.of();
        List<UUID> ids = plans.stream().map(Plan::getPublicId).toList();
        Set<UUID> referenced = planRepository.findReferencedPlanIds(ids);
        Set<UUID> outstanding = planRepository.findOutstandingCodePlanIds(ids, Instant.now());
        return plans.stream().map(plan -> PlanMapper.toResponse(plan, referenced.contains(plan.getPublicId()),
                outstanding.contains(plan.getPublicId()))).toList();
    }

    private void apply(Plan plan, PlanRequest request, PlanType type) {
        plan.setName(request.name());
        plan.setDescription(request.description());
        plan.setType(type);
        plan.setPrice(request.price());
        plan.setCurrency(request.currency());
        plan.setDurationDays(request.durationDays());
        plan.setMaxStudentsPerSession(request.maxStudentsPerSession());
        plan.setExtraStudentSlots(request.extraStudentSlots());
    }

    private PlanType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_REQUIRED);
        }
        try {
            return PlanType.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_INVALID);
        }
    }

    private void validate(String name, BigDecimal price, String currency, PlanType type,
            Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        if (name == null || name.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_REQUIRED);
        }
        if (price == null) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_REQUIRED);
        }
        if (price.signum() < 0) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_NON_NEGATIVE);
        }
        if (price.scale() > 2 || price.precision() - price.scale() > 17) {
            throw new PlanValidationException(BillingConstants.PLAN_PRICE_PRECISION_INVALID);
        }
        if (currency == null || currency.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_CURRENCY_REQUIRED);
        }
        if (currency.length() != 3) {
            throw new PlanValidationException(BillingConstants.PLAN_CURRENCY_INVALID);
        }

        if (type == PlanType.EXAM_PACKAGE) {
            if (!positive(durationDays)) {
                throw new PlanValidationException(BillingConstants.EXAM_DURATION_REQUIRED);
            }
            if (!positive(maxStudentsPerSession)) {
                throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_REQUIRED);
            }
            if (maxStudentsPerSession > BillingConstants.MAX_STUDENT_COUNT) {
                throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_LIMIT_EXCEEDED);
            }
            if (extraStudentSlots != null) {
                throw new PlanValidationException(BillingConstants.EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN);
            }
            return;
        }

        if (!positive(extraStudentSlots)) {
            throw new PlanValidationException(BillingConstants.CAPACITY_EXTRA_STUDENTS_REQUIRED);
        }
        if (extraStudentSlots > BillingConstants.MAX_STUDENT_COUNT) {
            throw new PlanValidationException(BillingConstants.CAPACITY_EXTRA_STUDENTS_LIMIT_EXCEEDED);
        }
        if (durationDays != null) {
            throw new PlanValidationException(BillingConstants.CAPACITY_DURATION_FORBIDDEN);
        }
        if (maxStudentsPerSession != null) {
            throw new PlanValidationException(BillingConstants.CAPACITY_MAX_STUDENTS_FORBIDDEN);
        }
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
