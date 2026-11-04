package com.pte.billing.internal.service.license;

import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.license.GrantLicenseRequest;
import com.pte.billing.internal.dto.request.license.PreviewLicenseGrantRequest;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.dto.response.license.LicenseGrantPreviewResponse;
import com.pte.billing.internal.dto.response.license.LicenseGrantResponse;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.identity.IdentityService;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin grant of a plan to an organization: access, idempotency, plan checks and the recorded outcome. */
@ExtendWith(MockitoExtension.class)
class LicenseGrantServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-03T10:15:30.123456789Z");

    @Mock
    private LicenseCodeRepository licenseCodeRepository;
    @Mock
    private ServicePlanRepository planRepository;
    @Mock
    private SubscriptionRepository subscriptionRepository;
    @Mock
    private SubscriptionActivationService activationService;
    @Mock
    private LicenseCodeGenerator codeGenerator;
    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private TenancyService tenancyService;
    @Mock
    private IdentityService identityService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private LicenseGrantService service;
    private CurrentUser admin;
    private UUID tenantId;
    private UUID key;

    @BeforeEach
    void setUp() {
        service = new LicenseGrantService(licenseCodeRepository, planRepository, subscriptionRepository,
                activationService, codeGenerator, idempotencyKeyService, tenancyService, identityService,
                auditLogService, eventPublisher, JsonMapper.builder().build(), Clock.fixed(NOW, ZoneOffset.UTC));
        admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        tenantId = UUID.randomUUID();
        key = UUID.randomUUID();
    }

    private ServicePlan plan(PlanType type, PlanStatus status) {
        ServicePlan plan = new ServicePlan();
        plan.setPublicId(UUID.randomUUID());
        plan.setName("Plan");
        plan.setType(type);
        plan.setStatus(status);
        plan.setPrice(BigDecimal.TEN);
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(40);
        plan.setExtraStudentSlots(25);
        return plan;
    }

    private GrantLicenseRequest request(UUID planId) {
        return new GrantLicenseRequest(tenantId, planId, null, "  CT-2026-01  ", "  ");
    }

    private void begin(IdempotencyOutcome outcome, String resultJson) {
        when(idempotencyKeyService.begin(eq(IdempotencyScope.LICENSE_ISSUE), anyString(), isNull(), anyString(),
                any())).thenReturn(new IdempotencyDecision(outcome, resultJson));
    }

    @Test
    void grant_callerWithoutLicenseIssueRight_isForbiddenBeforeAnyLock() {
        CurrentUser academic = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

        assertThatThrownBy(() -> service.grant(request(UUID.randomUUID()), key, academic))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        verify(tenancyService, never()).lockActiveTenant(any());
    }

    @Test
    void grant_inactiveOrganization_isConflictAndNoKeyIsStored() {
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.grant(request(UUID.randomUUID()), key, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(BillingConstants.LICENSE_GRANT_TENANT_NOT_ACTIVE);
                });
        verify(idempotencyKeyService, never()).begin(any(), anyString(), any(), anyString(), any());
    }

    @Test
    void grant_sameKeyWithDifferentRequest_isConflict() {
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.CONFLICT, null);

        assertThatThrownBy(() -> service.grant(request(UUID.randomUUID()), key, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REUSED);
                });
        verify(activationService, never()).activate(any(), any(), any(), any());
    }

    @Test
    void grant_repeatedKey_replaysTheStoredGrantWithoutActivatingAgain() {
        ServicePlan studentPlan = plan(PlanType.STUDENT_CAPACITY, PlanStatus.ACTIVE);
        LicenseCode stored = LicenseCode.grant("CODE", studentPlan.getPublicId(), admin.userId(), NOW, tenantId,
                null, "CT-2026-01", null);
        stored.setPublicId(UUID.randomUUID());
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.REPLAY, "{\"" + BillingConstants.LICENSE_ISSUE_RESULT_FIELD + "\":\""
                + stored.getPublicId() + "\"}");
        when(licenseCodeRepository.findByPublicId(stored.getPublicId())).thenReturn(Optional.of(stored));
        when(planRepository.findByPublicId(studentPlan.getPublicId())).thenReturn(Optional.of(studentPlan));

        LicenseGrantResponse response = service.grant(request(studentPlan.getPublicId()), key, admin);

        assertThat(response.replayed()).isTrue();
        assertThat(response.licenseCodePublicId()).isEqualTo(stored.getPublicId());
        assertThat(response.grantedStudentSlots()).isEqualTo(25);
        verify(activationService, never()).activate(any(), any(), any(), any());
    }

    @Test
    void grant_replayOfDeletedCode_isGone() {
        LicenseCode stored = LicenseCode.grant("CODE", UUID.randomUUID(), admin.userId(), NOW, tenantId, null,
                "CT", null);
        stored.setPublicId(UUID.randomUUID());
        stored.setDeleted(true);
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.REPLAY, "{\"" + BillingConstants.LICENSE_ISSUE_RESULT_FIELD + "\":\""
                + stored.getPublicId() + "\"}");
        when(licenseCodeRepository.findByPublicId(stored.getPublicId())).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.grant(request(UUID.randomUUID()), key, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.GONE));
    }

    @Test
    void grant_unknownPlan_isNotFound() {
        UUID planId = UUID.randomUUID();
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.NEW, null);
        when(planRepository.findByPublicIdForUpdate(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.grant(request(planId), key, admin)).isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void grant_planNotActive_isConflict() {
        ServicePlan draft = plan(PlanType.EXAM_PACKAGE, PlanStatus.DRAFT);
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.NEW, null);
        when(planRepository.findByPublicIdForUpdate(draft.getPublicId())).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.grant(request(draft.getPublicId()), key, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getCode()).isEqualTo(BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
                });
    }

    @Test
    void grant_codeGeneratorKeepsCollidingFiveTimes_failsAsRetryable() {
        ServicePlan examPlan = plan(PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE);
        Instant start = NOW.plusSeconds(60);
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.NEW, null);
        when(planRepository.findByPublicIdForUpdate(examPlan.getPublicId())).thenReturn(Optional.of(examPlan));
        when(activationService.resolveDefaultStart(tenantId, examPlan.getPublicId())).thenReturn(start);
        when(activationService.activate(tenantId, examPlan, ActivationSource.LICENSE_CODE, start)).thenReturn(
                SubscriptionActivationResponse.fromSubscription(tenantId, examPlan.getPublicId(), "KEY-1234",
                        start, start.plusSeconds(100), 40, "ACTIVE", "LICENSE_CODE", UUID.randomUUID()));
        when(codeGenerator.generate()).thenReturn("DUP");
        when(licenseCodeRepository.existsByCode("DUP")).thenReturn(true);

        assertThatThrownBy(() -> service.grant(request(examPlan.getPublicId()), key, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCode()).isEqualTo(BillingConstants.LICENSE_CODE_ISSUE_RETRYABLE);
                });
        verify(codeGenerator, times(5)).generate();
        verify(licenseCodeRepository, never()).saveAndFlush(any());
    }

    @Test
    void grant_examPackage_activatesRecordsRedeemedCodeAndPublishesOutcome() {
        ServicePlan examPlan = plan(PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE);
        Instant start = NOW.plusSeconds(60);
        UUID subscriptionId = UUID.randomUUID();
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.NEW, null);
        when(planRepository.findByPublicIdForUpdate(examPlan.getPublicId())).thenReturn(Optional.of(examPlan));
        when(activationService.resolveDefaultStart(tenantId, examPlan.getPublicId())).thenReturn(start);
        when(activationService.activate(tenantId, examPlan, ActivationSource.LICENSE_CODE, start)).thenReturn(
                SubscriptionActivationResponse.fromSubscription(tenantId, examPlan.getPublicId(), "KEY-1234",
                        start, start.plusSeconds(100), 40, "ACTIVE", "LICENSE_CODE", subscriptionId));
        when(codeGenerator.generate()).thenReturn("NEWCODE");
        when(licenseCodeRepository.existsByCode("NEWCODE")).thenReturn(false);
        when(licenseCodeRepository.saveAndFlush(any(LicenseCode.class))).thenAnswer(call -> {
            LicenseCode code = call.getArgument(0);
            code.setPublicId(UUID.randomUUID());
            return code;
        });

        LicenseGrantResponse response = service.grant(request(examPlan.getPublicId()), key, admin);

        assertThat(response.kind()).isEqualTo("SUBSCRIPTION");
        assertThat(response.replayed()).isFalse();
        assertThat(response.licenseKey()).isEqualTo("•••• 1234");
        assertThat(response.contractReference()).isEqualTo("CT-2026-01");
        assertThat(response.maxStudentsPerSession()).isEqualTo(40);
        assertThat(response.grantedStudentSlots()).isNull();
        assertThat(response.subscriptionPublicId()).isEqualTo(subscriptionId);

        ArgumentCaptor<CommercialOutcomeConfirmedEvent> event =
                ArgumentCaptor.forClass(CommercialOutcomeConfirmedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().outcomeType()).isEqualTo(CommercialOutcomeType.EXAM_PACKAGE);
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.LICENSE_ISSUE), anyString(), isNull(), anyString());
    }

    @Test
    void grant_studentCapacity_returnsSlotsAndNoStartDates() {
        ServicePlan capacity = plan(PlanType.STUDENT_CAPACITY, PlanStatus.ACTIVE);
        when(tenancyService.lockActiveTenant(tenantId)).thenReturn(true);
        begin(IdempotencyOutcome.NEW, null);
        when(planRepository.findByPublicIdForUpdate(capacity.getPublicId())).thenReturn(Optional.of(capacity));
        when(activationService.activate(tenantId, capacity, ActivationSource.LICENSE_CODE, null)).thenReturn(
                SubscriptionActivationResponse.forStudentCapacity(tenantId, capacity.getPublicId(), 25,
                        UUID.randomUUID()));
        when(codeGenerator.generate()).thenReturn("CAP1");
        when(licenseCodeRepository.saveAndFlush(any(LicenseCode.class))).thenAnswer(call -> {
            LicenseCode code = call.getArgument(0);
            code.setPublicId(UUID.randomUUID());
            return code;
        });

        LicenseGrantResponse response = service.grant(request(capacity.getPublicId()), key, admin);

        assertThat(response.kind()).isEqualTo("STUDENT_CAPACITY");
        assertThat(response.grantedStudentSlots()).isEqualTo(25);
        assertThat(response.maxStudentsPerSession()).isNull();
        assertThat(response.licenseKey()).isNull();
        verify(activationService, never()).resolveDefaultStart(any(), any());
    }

    @Test
    void preview_inactiveOrganization_isConflict() {
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(false);

        assertThatThrownBy(() -> service.preview(new PreviewLicenseGrantRequest(tenantId, null, null), admin))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void preview_withoutPlan_listsOnlyTheOrganizationHosts() {
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveByTenantIdAndRole(eq(tenantId), any())).thenReturn(List.of());

        LicenseGrantPreviewResponse preview = service.preview(new PreviewLicenseGrantRequest(tenantId, null, null),
                admin);

        assertThat(preview.hosts()).isEmpty();
        assertThat(preview.planId()).isNull();
        assertThat(preview.startsAt()).isNull();
    }

    @Test
    void preview_unknownPlan_isNotFound() {
        UUID planId = UUID.randomUUID();
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveByTenantIdAndRole(eq(tenantId), any())).thenReturn(List.of());
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.preview(new PreviewLicenseGrantRequest(tenantId, planId, null), admin))
                .isInstanceOf(PlanNotFoundException.class);
    }

    @Test
    void preview_studentCapacityPlan_showsSlotsWithoutDates() {
        ServicePlan capacity = plan(PlanType.STUDENT_CAPACITY, PlanStatus.ACTIVE);
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveByTenantIdAndRole(eq(tenantId), any())).thenReturn(List.of());
        when(planRepository.findByPublicId(capacity.getPublicId())).thenReturn(Optional.of(capacity));

        LicenseGrantPreviewResponse preview = service.preview(
                new PreviewLicenseGrantRequest(tenantId, capacity.getPublicId(), null), admin);

        assertThat(preview.planType()).isEqualTo("STUDENT_CAPACITY");
        assertThat(preview.startsAt()).isNull();
        assertThat(preview.expiresAt()).isNull();
    }

    @Test
    void preview_examPackageWithoutExplicitStart_usesTheDefaultContinuationAndPlanDuration() {
        ServicePlan examPlan = plan(PlanType.EXAM_PACKAGE, PlanStatus.ACTIVE);
        Instant start = NOW.plusSeconds(3_600);
        when(tenancyService.isActiveTenant(tenantId)).thenReturn(true);
        when(identityService.findActiveByTenantIdAndRole(eq(tenantId), any())).thenReturn(List.of());
        when(planRepository.findByPublicId(examPlan.getPublicId())).thenReturn(Optional.of(examPlan));
        when(activationService.resolveDefaultStart(tenantId, examPlan.getPublicId())).thenReturn(start);

        LicenseGrantPreviewResponse preview = service.preview(
                new PreviewLicenseGrantRequest(tenantId, examPlan.getPublicId(), null), admin);

        assertThat(preview.startsAt()).isEqualTo(start);
        assertThat(preview.expiresAt()).isEqualTo(start.plusSeconds(30L * 24 * 3_600));
        assertThat(preview.maxStudentsPerSession()).isEqualTo(40);
    }
}
