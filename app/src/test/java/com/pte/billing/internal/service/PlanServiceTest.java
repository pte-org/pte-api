package com.pte.billing.internal.service;

import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.PlanRequest;
import com.pte.billing.internal.dto.response.PlanResponse;
import com.pte.billing.internal.exception.PlanStateException;
import com.pte.billing.internal.exception.PlanValidationException;
import com.pte.billing.internal.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository);
    }

    @Test
    void create_examPackageRejectsExtraStudentSlotsWith422() {
        PlanRequest request = new PlanRequest("Exam", "Timed exam", "EXAM_PACKAGE",
                new BigDecimal("99.00"), "USD", 30, 25, 5);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN);
                });
    }

    @Test
    void create_studentCapacityRejectsExamFieldsWith422() {
        PlanRequest request = new PlanRequest("Capacity", "Extra seats", "STUDENT_CAPACITY",
                BigDecimal.ZERO, "USD", 30, null, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.CAPACITY_DURATION_FORBIDDEN);
                });
    }

    @Test
    void create_rejectsPriceThatDatabaseWouldRound() {
        PlanRequest request = new PlanRequest("Capacity", "Extra seats", "STUDENT_CAPACITY",
                new BigDecimal("10.123"), "USD", null, null, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.PLAN_PRICE_PRECISION_INVALID);
                });
    }

    @Test
    void create_acceptsBothPlanFamilies() {
        when(planRepository.save(any(Plan.class))).thenAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            plan.setPublicId(UUID.randomUUID());
            return plan;
        });

        PlanResponse exam = service.create(new PlanRequest("Exam", null, "EXAM_PACKAGE",
                new BigDecimal("99.00"), "USD", 30, 25, null));
        PlanResponse capacity = service.create(new PlanRequest("Capacity", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "USD", null, null, 10));

        assertThat(exam.type()).isEqualTo("EXAM_PACKAGE");
        assertThat(exam.status()).isEqualTo("DRAFT");
        assertThat(capacity.type()).isEqualTo("STUDENT_CAPACITY");
        assertThat(capacity.extraStudentSlots()).isEqualTo(10);
    }

    @Test
    void activate_onlyAllowsDraftPlans() {
        UUID publicId = UUID.randomUUID();
        Plan plan = plan(publicId, PlanStatus.DRAFT);
        when(planRepository.findByPublicId(publicId)).thenReturn(Optional.of(plan));

        PlanResponse response = service.activate(publicId);

        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void activate_activePlanIsRejected() {
        UUID publicId = UUID.randomUUID();
        when(planRepository.findByPublicId(publicId)).thenReturn(Optional.of(plan(publicId, PlanStatus.ACTIVE)));

        assertThatThrownBy(() -> service.activate(publicId))
                .isInstanceOf(PlanStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .isEqualTo(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE));
    }

    @Test
    void archiveHidesPlanFromActiveListButAdminCanStillGetIt() {
        UUID publicId = UUID.randomUUID();
        Plan plan = plan(publicId, PlanStatus.ACTIVE);
        when(planRepository.findByPublicId(publicId)).thenReturn(Optional.of(plan));
        when(planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE)).thenReturn(List.of());

        PlanResponse archived = service.archive(publicId);
        List<PlanResponse> active = service.listActive();
        PlanResponse adminView = service.get(publicId);

        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(active).isEmpty();
        assertThat(adminView.status()).isEqualTo("ARCHIVED");
    }

    private Plan plan(UUID publicId, PlanStatus status) {
        Plan plan = new Plan();
        plan.setPublicId(publicId);
        plan.setName("Plan");
        plan.setType(com.pte.billing.domain.enums.PlanType.STUDENT_CAPACITY);
        plan.setPrice(BigDecimal.ZERO);
        plan.setCurrency("USD");
        plan.setExtraStudentSlots(10);
        plan.setStatus(status);
        return plan;
    }
}
