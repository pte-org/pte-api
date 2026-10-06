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
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private AuditLogService auditLogService;

    private PlanService service;

    @BeforeEach
    void setUp() {
        service = new PlanService(planRepository, auditLogService);
        lenient().when(planRepository.findReferencedPlanIds(any())).thenReturn(Set.of());
        lenient().when(planRepository.findOutstandingCodePlanIds(any(), any())).thenReturn(Set.of());
        lenient().doAnswer(invocation -> {
            Plan plan = invocation.getArgument(0);
            if (plan.getPublicId() == null) {
                plan.setPublicId(UUID.randomUUID());
            } else {
                plan.setVersion(plan.getVersion() + 1);
            }
            return plan;
        }).when(planRepository).saveAndFlush(any(Plan.class));
    }

    @Test
    void createExamPackageRejectsExtraStudentSlotsWith422() {
        PlanRequest request = new PlanRequest("Exam", "Timed exam", "EXAM_PACKAGE",
                new BigDecimal("99.00"), "VND", 30, 25, 5);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.EXAM_EXTRA_STUDENT_SLOTS_FORBIDDEN);
                });
    }

    @Test
    void createStudentCapacityRejectsExamFieldsWith422() {
        PlanRequest request = new PlanRequest("Capacity", "Extra seats", "STUDENT_CAPACITY",
                BigDecimal.ZERO, "VND", 30, null, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.CAPACITY_DURATION_FORBIDDEN);
                });
    }

    @Test
    void createRejectsPriceThatDatabaseWouldRound() {
        PlanRequest request = new PlanRequest("Capacity", "Extra seats", "STUDENT_CAPACITY",
                new BigDecimal("10.123"), "VND", null, null, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOfSatisfying(PlanValidationException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.PLAN_PRICE_PRECISION_INVALID);
                });
    }

    @Test
    void createRejectsNonVndCurrency() {
        PlanRequest request = new PlanRequest("Capacity", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "USD", null, null, 10);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(PlanValidationException.class)
                .hasMessage(BillingConstants.PLAN_CURRENCY_INVALID);
    }

    @Test
    void createRejectsNameAndDurationBeyondBusinessBounds() {
        String tooLongName = "x".repeat(256);
        assertThatThrownBy(() -> service.create(new PlanRequest(tooLongName, null, "EXAM_PACKAGE",
                BigDecimal.ZERO, "VND", 30, 10, null)))
                .hasMessage(BillingConstants.PLAN_NAME_MAX);

        assertThatThrownBy(() -> service.create(new PlanRequest("Exam", null, "EXAM_PACKAGE",
                BigDecimal.ZERO, "VND", 3651, 10, null)))
                .hasMessage(BillingConstants.EXAM_DURATION_LIMIT_EXCEEDED);
    }

    @Test
    void updateRejectsNegativeExpectedVersionBeforeMutation() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.update(id, new PlanUpdateRequest("New", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "VND", null, null, 20, -1L)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessage(BillingConstants.PLAN_VERSION_INVALID);
        assertThat(plan.getName()).isEqualTo("Plan");
    }

    @Test
    void transitionRejectsStaleExpectedVersion() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        plan.setVersion(2L);
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.activate(id, new PlanTransitionRequest(1L)))
                .isInstanceOf(PlanLifecycleException.class)
                .hasMessage(BillingConstants.PLAN_VERSION_CONFLICT);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
    }

    @Test
    void createAcceptsBothPlanFamilies() {
        PlanResponse exam = service.create(new PlanRequest("Exam", null, "EXAM_PACKAGE",
                new BigDecimal("99.00"), "VND", 30, 25, null));
        PlanResponse capacity = service.create(new PlanRequest("Capacity", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "VND", null, null, 10));

        assertThat(exam.type()).isEqualTo("EXAM_PACKAGE");
        assertThat(exam.status()).isEqualTo("DRAFT");
        assertThat(capacity.type()).isEqualTo("STUDENT_CAPACITY");
        assertThat(capacity.extraStudentSlots()).isEqualTo(10);
    }

    @Test
    void activateOnlyAllowsDraftPlansAndReturnsIncrementedVersion() {
        UUID publicId = UUID.randomUUID();
        Plan plan = plan(publicId, PlanStatus.DRAFT);
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(plan));

        PlanResponse response = service.activate(publicId, new PlanTransitionRequest(0L));

        assertThat(response.status()).isEqualTo("ACTIVE");
        assertThat(response.version()).isEqualTo(1L);
    }

    @Test
    void activateActivePlanIsRejected() {
        UUID publicId = UUID.randomUUID();
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(plan(publicId, PlanStatus.ACTIVE)));

        assertThatThrownBy(() -> service.activate(publicId, new PlanTransitionRequest(0L)))
                .isInstanceOf(PlanStateException.class)
                .satisfies(ex -> assertThat(ex.getMessage())
                        .isEqualTo(BillingConstants.PLAN_MUST_BE_DRAFT_TO_ACTIVATE));
    }

    @Test
    void activateRejectsLegacyInvalidCurrencyBeforePublishing() {
        UUID publicId = UUID.randomUUID();
        Plan legacy = plan(publicId, PlanStatus.DRAFT);
        legacy.setCurrency("USD");
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(legacy));

        assertThatThrownBy(() -> service.activate(publicId, new PlanTransitionRequest(0L)))
                .isInstanceOf(PlanValidationException.class)
                .hasMessage(BillingConstants.PLAN_CURRENCY_INVALID);
    }

    @Test
    void archiveHidesPlanFromActiveListButAdminCanStillGetIt() {
        UUID publicId = UUID.randomUUID();
        Plan plan = plan(publicId, PlanStatus.ACTIVE);
        when(planRepository.findByPublicId(publicId)).thenReturn(Optional.of(plan));
        when(planRepository.findByPublicIdForUpdate(publicId)).thenReturn(Optional.of(plan));
        when(planRepository.findByStatusOrderByCreatedAtDesc(PlanStatus.ACTIVE)).thenReturn(List.of());

        PlanResponse archived = service.archive(publicId, new PlanTransitionRequest(0L));
        List<PlanResponse> active = service.listActive();
        PlanResponse adminView = service.get(publicId);

        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(active).isEmpty();
        assertThat(adminView.status()).isEqualTo("ARCHIVED");
    }

    @Test
    void deleteUnusedDraftIsSoftAndAuditedOnlyOnce() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        CurrentUser admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));
        service.deleteDraft(id, admin);
        service.deleteDraft(id, admin);
        assertThat(plan.isDeleted()).isTrue();
        verify(auditLogService).record(admin, BillingConstants.PLAN_AGGREGATE,
                id.toString(), BillingConstants.PLAN_DRAFT_DELETED, BillingConstants.PLAN_DRAFT_DELETED_SUMMARY);
    }

    @Test
    void deleteRejectsAnyHistoricalReference() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));
        when(planRepository.findReferencedPlanIds(List.of(id))).thenReturn(Set.of(id));
        assertThatThrownBy(() -> service.deleteDraft(id,
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"))))
                .isInstanceOf(PlanLifecycleException.class)
                .hasMessage(BillingConstants.PLAN_HAS_REFERENCES);
        assertThat(plan.isDeleted()).isFalse();
        verifyNoInteractions(auditLogService);
    }

    @Test
    void archiveDraftIsNotAReplacementForDelete() {
        UUID id = UUID.randomUUID();
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan(id, PlanStatus.DRAFT)));
        assertThatThrownBy(() -> service.archive(id, new PlanTransitionRequest(0L)))
                .hasMessage(BillingConstants.PLAN_MUST_BE_ACTIVE_TO_ARCHIVE);
    }

    @Test
    void outstandingCodesBlockArchiveAndBenefitChanges() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.ACTIVE);
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));
        when(planRepository.findOutstandingCodePlanIds(org.mockito.ArgumentMatchers.eq(List.of(id)), any()))
                .thenReturn(Set.of(id));
        assertThatThrownBy(() -> service.archive(id, new PlanTransitionRequest(0L)))
                .hasMessage(BillingConstants.PLAN_HAS_OUTSTANDING_CODES);
        assertThatThrownBy(() -> service.update(id, new PlanUpdateRequest("New", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "VND", null, null, 20, 0L)))
                .hasMessage(BillingConstants.PLAN_HAS_OUTSTANDING_CODES);
        assertThat(plan.getStatus()).isEqualTo(PlanStatus.ACTIVE);
        assertThat(plan.getExtraStudentSlots()).isEqualTo(10);
    }

    @Test
    void staleUpdateIsRejectedWithoutOverwritingTheLockedPlan() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        plan.setVersion(3L);
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.update(id, new PlanUpdateRequest("Stale", null, "STUDENT_CAPACITY",
                BigDecimal.ZERO, "VND", null, null, 20, 2L)))
                .isInstanceOf(PlanLifecycleException.class)
                .satisfies(ex -> {
                    PlanLifecycleException conflict = (PlanLifecycleException) ex;
                    assertThat(conflict.getCode()).isEqualTo(BillingConstants.PLAN_VERSION_CONFLICT);
                    assertThat(conflict.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                });
        assertThat(plan.getName()).isEqualTo("Plan");
    }

    @Test
    void tombstoneCannotBeReadOrActivated() {
        UUID id = UUID.randomUUID();
        Plan plan = plan(id, PlanStatus.DRAFT);
        plan.setDeleted(true);
        when(planRepository.findByPublicId(id)).thenReturn(Optional.of(plan));
        when(planRepository.findByPublicIdForUpdate(id)).thenReturn(Optional.of(plan));
        assertThatThrownBy(() -> service.get(id)).isInstanceOf(PlanNotFoundException.class);
        assertThatThrownBy(() -> service.activate(id, new PlanTransitionRequest(0L)))
                .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void nonAdminCannotDeleteEvenKnownId() {
        assertThatThrownBy(() -> service.deleteDraft(UUID.randomUUID(),
                new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_AUTHOR"))))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(planRepository);
    }

    private Plan plan(UUID publicId, PlanStatus status) {
        Plan plan = new Plan();
        plan.setPublicId(publicId);
        plan.setName("Plan");
        plan.setType(PlanType.STUDENT_CAPACITY);
        plan.setPrice(BigDecimal.ZERO);
        plan.setCurrency("VND");
        plan.setExtraStudentSlots(10);
        plan.setStatus(status);
        return plan;
    }
}
