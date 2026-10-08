package com.pte.billing.internal.service;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.billing.internal.dto.request.PlanTransitionRequest;
import com.pte.billing.internal.dto.request.PlanUpdateRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.exception.PlanLifecycleException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.exception.PlanStateException;
import com.pte.billing.internal.exception.PlanValidationException;
import com.pte.billing.internal.mapper.PlanMapper;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import com.pte.shared.constant.SharedConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Platform plan catalog and its explicit draft/active/archived lifecycle. */
@Service
public class PlanService {

    private static final int MAX_NAME_LENGTH = 255;
    private static final int MAX_DESCRIPTION_LENGTH = 255;
    private static final int MAX_EXAM_DURATION_DAYS = 3650;

    private final PlanRepository planRepository;
    private final AuditLogService auditLogService;

    @Autowired
    public PlanService(PlanRepository planRepository, AuditLogService auditLogService) {
        this.planRepository = planRepository;
        this.auditLogService = auditLogService;
    }

    /** Compatibility constructor for existing focused tests. */
    public PlanService(PlanRepository planRepository) {
        this(planRepository, null);
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
        PlanType type = parseType(request.type());
        validate(request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());

        Plan plan = new Plan();
        apply(plan, request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        PlanResponse result = response(persist(plan));
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
        return response(find(publicId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin() {
        return listForAdmin(null);
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listForAdmin(CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_READ_ALL);
        return responses(planRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(plan -> !plan.isDeleted())
                .toList());
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listActive() {
        return responses(planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE).stream()
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
        Plan plan = findForUpdate(publicId);
        requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ARCHIVED_NOT_EDITABLE);
        }

        PlanType type = parseType(request.type());
        if (plan.getStatus() == PlanStatus.ACTIVE && type != plan.getType()) {
            throw new PlanLifecycleException(BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE,
                    BillingConstants.PLAN_ACTIVE_TYPE_IMMUTABLE_MESSAGE);
        }
        validate(request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        if (type != plan.getType() || !Objects.equals(request.durationDays(), plan.getDurationDays())
                || !Objects.equals(request.maxStudentsPerSession(), plan.getMaxStudentsPerSession())
                || !Objects.equals(request.extraStudentSlots(), plan.getExtraStudentSlots())) {
            requireNoOutstandingCodes(plan);
        }

        apply(plan, request.name(), request.description(), request.price(), request.currency(), type,
                request.durationDays(), request.maxStudentsPerSession(), request.extraStudentSlots());
        PlanResponse result = response(persist(plan));
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
        Plan plan = findForUpdate(publicId);
        requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() != PlanStatus.DRAFT) {
            throw new PlanStateException(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE);
        }
        validate(plan.getName(), plan.getDescription(), plan.getPrice(), plan.getCurrency(), plan.getType(),
                plan.getDurationDays(), plan.getMaxStudentsPerSession(), plan.getExtraStudentSlots());
        plan.activate();
        PlanResponse result = response(persist(plan));
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
        Plan plan = findForUpdate(publicId);
        requireExpectedVersion(plan, request.expectedVersion());
        if (plan.getStatus() == PlanStatus.ARCHIVED) {
            throw new PlanStateException(BillingConstants.PLAN_ALREADY_ARCHIVED);
        }
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new PlanLifecycleException(BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE,
                    BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE_MESSAGE);
        }
        requireNoOutstandingCodes(plan);
        plan.archive();
        PlanResponse result = response(persist(plan));
        audit(caller, BillingConstants.AUDIT_PLAN_TRANSITION, publicId, BillingConstants.PLAN_ARCHIVED_SUMMARY);
        return result;
    }

    @Transactional
    public void deleteDraft(UUID publicId, CurrentUser caller) {
        require(caller, PlatformOperation.PLAN_DRAFT_WRITE);
        Plan plan = planRepository.findByPublicIdForUpdate(publicId)
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

    private Plan find(UUID publicId) {
        return planRepository.findByPublicId(publicId)
                .filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    private Plan findForUpdate(UUID publicId) {
        return planRepository.findByPublicIdForUpdate(publicId)
                .filter(plan -> !plan.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
    }

    private void requireExpectedVersion(Plan plan, Long expectedVersion) {
        if (expectedVersion == null || expectedVersion < 0) {
            throw new PlanValidationException(BillingConstants.PLAN_VERSION_INVALID);
        }
        if (expectedVersion.longValue() != plan.getVersion()) {
            throw versionConflict();
        }
    }

    private Plan persist(Plan plan) {
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

    private void requireNoOutstandingCodes(Plan plan) {
        if (planRepository.findOutstandingCodePlanIds(List.of(plan.getPublicId()), Instant.now())
                .contains(plan.getPublicId())) {
            throw new PlanLifecycleException(BillingConstants.PLAN_HAS_OUTSTANDING_CODES,
                    BillingConstants.PLAN_HAS_OUTSTANDING_CODES_MESSAGE);
        }
    }

    private PlanResponse response(Plan plan) {
        return responses(List.of(plan)).getFirst();
    }

    private List<PlanResponse> responses(List<Plan> plans) {
        if (plans.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = plans.stream().map(Plan::getPublicId).toList();
        Set<UUID> referenced = planRepository.findReferencedPlanIds(ids);
        Set<UUID> outstanding = planRepository.findOutstandingCodePlanIds(ids, Instant.now());
        return plans.stream()
                .map(plan -> PlanMapper.toResponse(plan, referenced.contains(plan.getPublicId()),
                        outstanding.contains(plan.getPublicId())))
                .toList();
    }

    private void apply(Plan plan, String name, String description, BigDecimal price, String currency,
            PlanType type, Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        plan.setName(name.trim());
        plan.setDescription(description == null || description.trim().isEmpty() ? null : description.trim());
        plan.setType(type);
        plan.setPrice(price);
        plan.setCurrency(currency.trim().toUpperCase(Locale.ROOT));
        plan.setDurationDays(durationDays);
        plan.setMaxStudentsPerSession(maxStudentsPerSession);
        plan.setExtraStudentSlots(extraStudentSlots);
    }

    private PlanType parseType(String value) {
        if (value == null || value.isBlank()) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_REQUIRED);
        }
        try {
            return PlanType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_INVALID);
        }
    }

    private void validate(String name, String description, BigDecimal price, String currency, PlanType type,
            Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        String normalizedName = name == null ? null : name.trim();
        if (normalizedName == null || normalizedName.isEmpty()) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_REQUIRED);
        }
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new PlanValidationException(BillingConstants.PLAN_NAME_MAX);
        }
        if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            throw new PlanValidationException(BillingConstants.PLAN_DESCRIPTION_MAX);
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
        if (!"VND".equals(currency.trim().toUpperCase(Locale.ROOT))) {
            throw new PlanValidationException(BillingConstants.PLAN_CURRENCY_INVALID);
        }
        if (type == null) {
            throw new PlanValidationException(BillingConstants.PLAN_TYPE_INVALID);
        }

        if (type == PlanType.EXAM_PACKAGE) {
            validateExamBenefits(durationDays, maxStudentsPerSession, extraStudentSlots);
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

    static void validateExamBenefits(Integer durationDays, Integer maxStudentsPerSession, Integer extraStudentSlots) {
        if (!positive(durationDays)) throw new PlanValidationException(BillingConstants.EXAM_DURATION_REQUIRED);
        if (durationDays > MAX_EXAM_DURATION_DAYS) throw new PlanValidationException(BillingConstants.EXAM_DURATION_LIMIT_EXCEEDED);
        if (!positive(maxStudentsPerSession)) throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_REQUIRED);
        if (maxStudentsPerSession > BillingConstants.MAX_STUDENT_COUNT) throw new PlanValidationException(BillingConstants.EXAM_MAX_STUDENTS_LIMIT_EXCEEDED);
        if (extraStudentSlots != null) throw new PlanValidationException(BillingConstants.EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN);
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
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
