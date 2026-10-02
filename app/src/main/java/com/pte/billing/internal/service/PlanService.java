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

    public PlanService(PlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @Transactional
    public PlanResponse create(PlanRequest request) {
        PlanType type = parseType(request.type());
        validate(request.name(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());

        Plan plan = new Plan();
        apply(plan, request, type);
        return PlanMapper.toResponse(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public PlanResponse get(UUID publicId) {
        return PlanMapper.toResponse(find(publicId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin() {
        return planRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PlanMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActive() {
        return planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE).stream()
                .map(PlanMapper::toResponse)
                .toList();
    }

    @Transactional
    public PlanResponse update(UUID publicId, PlanRequest request) {
        Plan plan = find(publicId);
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ARCHIVED_NOT_EDITABLE);
        }

        PlanType type = parseType(request.type());
        validate(request.name(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        apply(plan, request, type);
        return PlanMapper.toResponse(planRepository.save(plan));
    }

    @Transactional
    public PlanResponse activate(UUID publicId) {
        Plan plan = find(publicId);
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanStateException(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE);
        }
        plan.activate();
        return PlanMapper.toResponse(plan);
    }

    @Transactional
    public PlanResponse archive(UUID publicId) {
        Plan plan = find(publicId);
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ALREADY_ARCHIVED);
        }
        plan.archive();
        return PlanMapper.toResponse(plan);
    }

    private Plan find(UUID publicId) {
        return planRepository.findByPublicId(publicId)
                .orElseThrow(PlanNotFoundException::new);
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
