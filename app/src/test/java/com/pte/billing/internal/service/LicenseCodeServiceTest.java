package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.audit.AuditLogService;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.TenantSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseCodeServiceTest {

    @Mock
    private LicenseCodeRepository licenseCodeRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private LicenseCodePersistenceService licenseCodePersistenceService;

    @Mock
    private LicenseCodeGenerator licenseCodeGenerator;

    @Mock
    private SubscriptionActivationService subscriptionActivationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TenancyService tenancyService;

    private LicenseCodeService service;

    @Test void replayDoesNotValidateCurrentPlanOrGenerateAnotherCode() {
        UUID planId = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        CurrentUser admin = platformAdmin();
        LicenseIssueReceipt receipt = new LicenseIssueReceipt(UUID.randomUUID(), planId, "ISSUED", "EXPIRED",
                Instant.EPOCH, Instant.EPOCH.plusSeconds(1), true);
        when(licenseCodePersistenceService.replay(eq(admin.userId()), eq(key), any())).thenReturn(Optional.of(receipt));
        assertThat(service.issue(planId, receipt.codeExpiresAt(), key, admin)).isSameAs(receipt);
        verify(licenseCodeGenerator, never()).generate();
        verify(planRepository, never()).findByPublicId(any());
    }

    @Test void replayStillRequiresCurrentPlatformAuthorization() {
        assertThatThrownBy(() -> service.issue(UUID.randomUUID(), null, UUID.randomUUID(), hostAdmin(UUID.randomUUID())))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        org.mockito.Mockito.verifyNoInteractions(licenseCodePersistenceService);
    }

    @Test void finerThanMicrosecondPrecisionFailsBeforeIntentLookup() {
        assertThatThrownBy(() -> service.issue(UUID.randomUUID(), Instant.parse("2030-01-01T00:00:00.000000001Z"),
                UUID.randomUUID(), platformAdmin())).isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        org.mockito.Mockito.verifyNoInteractions(licenseCodePersistenceService);
    }

    @Test void unrelatedIntegrityFailureIsNeverRetriedAsCodeCollision() {
        when(licenseCodeGenerator.generate()).thenReturn("FIXTURE");
        when(licenseCodePersistenceService.issue(any(), any(), any())).thenThrow(
                new org.springframework.dao.DataIntegrityViolationException("foreign key fixture"));
        assertThatThrownBy(() -> service.issue(UUID.randomUUID(), null, UUID.randomUUID(), platformAdmin()))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_ISSUE_FAILED));
        verify(licenseCodeGenerator, org.mockito.Mockito.times(1)).generate();
    }

    @Test void recognizedCodeCollisionRetriesWholeTransaction() {
        when(licenseCodeGenerator.generate()).thenReturn("COLLISION", "SUCCESS");
        var violation = new org.hibernate.exception.ConstraintViolationException("fixture", new java.sql.SQLException(),
                "license_codes_code_key");
        LicenseIssueReceipt receipt = new LicenseIssueReceipt(UUID.randomUUID(), UUID.randomUUID(), "ISSUED", "ISSUED",
                Instant.EPOCH, null, false);
        when(licenseCodePersistenceService.issue(any(), any(), any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fixture", violation)).thenReturn(receipt);
        assertThat(service.issue(receipt.planId(), null, UUID.randomUUID(), platformAdmin())).isEqualTo(receipt);
        verify(licenseCodeGenerator, org.mockito.Mockito.times(2)).generate();
    }

    @Test void effectiveExpiryHasExactMicrosecondBoundaryAndKeepsRedeemedState() {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");
        for (long offset : new long[]{-1, 0, 1}) {
            LicenseCode code = LicenseCode.issue("FIXTURE", UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH,
                    now.plus(offset, java.time.temporal.ChronoUnit.MICROS));
            assertThat(LicenseCodeStateResolver.resolve(code, now))
                    .isEqualTo(offset > 0 ? LicenseCodeStatus.ISSUED : LicenseCodeStatus.EXPIRED);
            code.markRedeemed(UUID.randomUUID(), Instant.EPOCH);
            assertThat(LicenseCodeStateResolver.resolve(code, now)).isEqualTo(LicenseCodeStatus.REDEEMED);
        }
    }

    @BeforeEach
    void setUp() {
        service = new LicenseCodeService(licenseCodeRepository, planRepository, subscriptionRepository,
                licenseCodePersistenceService, licenseCodeGenerator, subscriptionActivationService, eventPublisher,
                auditLogService, tenancyService, java.time.Clock.systemUTC());
    }

    @Test
    void adminPageMasksBearerAndBatchEnrichesPlanAndRecipient() {
        UUID planId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        LicenseCode code = LicenseCode.issue("ABCD-EFGH-JKLM-NPQR-STUV", planId, UUID.randomUUID(),
                Instant.now(), null);
        code.setPublicId(UUID.randomUUID());
        code.markRedeemed(tenantId, Instant.now());
        Plan plan = activePlan(planId);
        when(licenseCodeRepository.findAdminPage(eq(LicenseCodeStatus.REDEEMED), eq(planId), eq(tenantId),
                any(Instant.class), eq(PageRequest.of(0, 25))))
                .thenReturn(new PageImpl<>(List.of(code), PageRequest.of(0, 25), 1));
        when(planRepository.findByPublicIdIn(any())).thenReturn(List.of(plan));
        when(tenancyService.findTenantSummaries(any())).thenReturn(
                Map.of(tenantId, new TenantSummary(tenantId, "Tenant A", false)));

        AdminLicenseCodeSummary result = service.listForAdmin(0, 25, "redeemed", planId, tenantId,
                platformAdmin()).data().getFirst();

        assertThat(result.maskedCode()).isEqualTo("•••• STUV");
        assertThat(result.effectiveStatus()).isEqualTo("REDEEMED");
        assertThat(result.planName()).isEqualTo(plan.getName());
        assertThat(result.recipientName()).isEqualTo("Tenant A");
        assertThat(java.util.Arrays.stream(AdminLicenseCodeSummary.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("code"))).isTrue();
        verify(planRepository).findByPublicIdIn(any());
        verify(tenancyService).findTenantSummaries(any());
    }

    @Test
    void managerPageAllowsIssuedCodeWithoutRecipient() {
        UUID planId = UUID.randomUUID();
        LicenseCode code = LicenseCode.issue("ABCD-EFGH-JKLM-NPQR-STUV", planId, UUID.randomUUID(),
                Instant.now(), null);
        code.setPublicId(UUID.randomUUID());
        Plan plan = activePlan(planId);
        when(licenseCodeRepository.findAdminPage(eq(LicenseCodeStatus.ISSUED), eq(null), eq(null),
                any(Instant.class), eq(PageRequest.of(0, 25))))
                .thenReturn(new PageImpl<>(List.of(code), PageRequest.of(0, 25), 1));
        when(planRepository.findByPublicIdIn(any())).thenReturn(List.of(plan));
        when(tenancyService.findTenantSummaries(any())).thenReturn(Map.of());

        AdminLicenseCodeSummary result = service.listForAdmin(0, 25, "issued", null, null,
                platformManager()).data().getFirst();

        assertThat(result.effectiveStatus()).isEqualTo("ISSUED");
        assertThat(result.recipientPublicId()).isNull();
        assertThat(result.recipientName()).isNull();
        assertThat(result.planName()).isEqualTo(plan.getName());
    }

    @Test
    void adminPageRejectsUnboundedInputsAndUnknownStatus() {
        CurrentUser admin = platformAdmin();
        assertThatThrownBy(() -> service.listForAdmin(-1, 25, null, null, null, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.listForAdmin(0, 101, null, null, null, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> service.listForAdmin(0, 25, "bearer", null, null, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        org.mockito.Mockito.verifyNoInteractions(licenseCodeRepository);
    }

    @Test
    void issue_returnsGeneratedCodeForActivePlan() {
        UUID planId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(3600).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        when(licenseCodeGenerator.generate()).thenReturn("ABCD-EFGH-JKLM-NPQR-STUV");
        when(licenseCodePersistenceService.issue(any(LicenseCode.class), any(), any())).thenAnswer(invocation -> {
            LicenseCode code = invocation.getArgument(0);
            code.setPublicId(UUID.randomUUID());
            return new LicenseIssueReceipt(code.getPublicId(), code.getPlanId(), "ISSUED", "ISSUED",
                    code.getIssuedAt(), code.getCodeExpiresAt(), false);
        });

        LicenseIssueReceipt response = service.issue(planId, expiry, UUID.randomUUID(),
                new CurrentUser(adminId, null, List.of("PLATFORM_ADMIN")));

        assertThat(response.replayed()).isFalse();
        assertThat(response.planId()).isEqualTo(planId);
        assertThat(response.status()).isEqualTo(LicenseCodeStatus.ISSUED.name());
    }

    @Test
    void managerCanIssueLicenseAndReceivesOnlySafeReceipt() {
        UUID planId = UUID.randomUUID();
        when(licenseCodeGenerator.generate()).thenReturn("ABCD-EFGH-JKLM-NPQR-STUV");
        when(licenseCodePersistenceService.issue(any(LicenseCode.class), any(), any())).thenAnswer(invocation -> {
            LicenseCode code = invocation.getArgument(0);
            code.setPublicId(UUID.randomUUID());
            return new LicenseIssueReceipt(code.getPublicId(), code.getPlanId(), "ISSUED", "ISSUED",
                    code.getIssuedAt(), code.getCodeExpiresAt(), false);
        });

        LicenseIssueReceipt response = service.issue(planId, null, UUID.randomUUID(), platformManager());

        assertThat(response.planId()).isEqualTo(planId);
        assertThat(java.util.Arrays.stream(LicenseIssueReceipt.class.getDeclaredFields())
                .noneMatch(field -> field.getName().equals("code"))).isTrue();
    }

    @Test
    void managerCannotRevealOrPreviewRevokeLicense() {
        UUID codeId = UUID.randomUUID();

        assertThatThrownBy(() -> service.revealForAdmin(codeId, platformManager()))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> service.previewRevoke(codeId, platformManager()))
                .isInstanceOfSatisfying(LicenseCodeException.class,
                        ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        org.mockito.Mockito.verifyNoInteractions(licenseCodeRepository);
    }

    @Test
    void issue_rejectsInactivePlan() {
        UUID planId = UUID.randomUUID();
        when(licenseCodeGenerator.generate()).thenReturn("FIXTURE");
        when(licenseCodePersistenceService.issue(any(), any(), any())).thenThrow(
                new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE));

        assertThatThrownBy(() -> service.issue(planId, null, UUID.randomUUID(), platformAdmin()))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
                });
        verify(planRepository, never()).findByPublicId(any());
    }

    @Test
    void redeem_marksCodeAtomicallyAndActivatesOnce() {
        UUID tenantId = UUID.randomUUID();
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        SubscriptionActivationResponse activation = SubscriptionActivationResponse.fromSubscription(
                tenantId, code.getPlanId(), "PTE-EXAM-2026-ABC123", Instant.now(),
                Instant.now().plusSeconds(3600), 25, "ACTIVE", ActivationSource.LICENSE_CODE.name(),
                subscription.getPublicId());
        when(licenseCodeRepository.markRedeemed(eq(code.getCode()), eq(tenantId), any(),
                eq(LicenseCodeStatus.ISSUED), eq(LicenseCodeStatus.REDEEMED)))
                .thenAnswer(invocation -> {
                    code.markRedeemed(tenantId, invocation.getArgument(2));
                    return 1;
                });
        when(licenseCodeRepository.findByCodeForUpdate(code.getCode())).thenReturn(Optional.of(code));
        when(planRepository.findByPublicIdForUpdate(code.getPlanId())).thenReturn(Optional.of(activePlan(code.getPlanId())));
        when(subscriptionActivationService.activate(eq(tenantId), any(Plan.class), eq(ActivationSource.LICENSE_CODE)))
                .thenReturn(activation);
        when(subscriptionRepository.findByLicenseKey(activation.licenseKey())).thenReturn(Optional.of(subscription));
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        SubscriptionActivationResponse result = service.redeem("  " + code.getCode().toLowerCase() + "  ",
                hostAdmin(tenantId));

        assertThat(result).isSameAs(activation);
        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REDEEMED);
        assertThat(code.getRedeemedByTenantId()).isEqualTo(tenantId);
        assertThat(code.getSubscriptionId()).isEqualTo(subscription.getPublicId());
        verify(licenseCodeRepository).markRedeemed(eq(code.getCode()), eq(tenantId), any(),
                eq(LicenseCodeStatus.ISSUED), eq(LicenseCodeStatus.REDEEMED));
        verify(subscriptionActivationService).activate(eq(tenantId), any(Plan.class), eq(ActivationSource.LICENSE_CODE));
        ArgumentCaptor<CommercialOutcomeConfirmedEvent> eventCaptor =
                ArgumentCaptor.forClass(CommercialOutcomeConfirmedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().outcomeType()).isEqualTo(CommercialOutcomeType.EXAM_PACKAGE);
        assertThat(eventCaptor.getValue().targetType()).isEqualTo(CommercialActivationTarget.SUBSCRIPTION);
        assertThat(eventCaptor.getValue().targetPublicId()).isEqualTo(subscription.getPublicId());
    }

    @Test
    void redeem_sameCodeTwiceActivatesOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        SubscriptionActivationResponse activation = SubscriptionActivationResponse.fromSubscription(
                tenantId, code.getPlanId(), "PTE-EXAM-2026-ABC123", Instant.now(),
                Instant.now().plusSeconds(3600), 25, "ACTIVE", ActivationSource.LICENSE_CODE.name(),
                subscription.getPublicId());
        when(licenseCodeRepository.markRedeemed(eq(code.getCode()), eq(tenantId), any(),
                eq(LicenseCodeStatus.ISSUED), eq(LicenseCodeStatus.REDEEMED)))
                .thenAnswer(invocation -> {
                    if (code.getStatus() == LicenseCodeStatus.ISSUED) {
                        code.markRedeemed(tenantId, invocation.getArgument(2));
                        return 1;
                    }
                    return 0;
                });
        when(licenseCodeRepository.findByCodeForUpdate(code.getCode())).thenReturn(Optional.of(code));
        when(licenseCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));
        when(planRepository.findByPublicIdForUpdate(code.getPlanId())).thenReturn(Optional.of(activePlan(code.getPlanId())));
        when(subscriptionActivationService.activate(eq(tenantId), any(Plan.class), eq(ActivationSource.LICENSE_CODE)))
                .thenReturn(activation);
        when(subscriptionRepository.findByLicenseKey(activation.licenseKey())).thenReturn(Optional.of(subscription));
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        service.redeem(code.getCode(), hostAdmin(tenantId));

        assertThatThrownBy(() -> service.redeem(code.getCode(), hostAdmin(tenantId)))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_ALREADY_REDEEMED);
                });
        verify(subscriptionActivationService).activate(eq(tenantId), any(Plan.class), eq(ActivationSource.LICENSE_CODE));
        verify(eventPublisher, org.mockito.Mockito.times(1))
                .publishEvent(any(CommercialOutcomeConfirmedEvent.class));
    }

    @ParameterizedTest
    @MethodSource("nonRedeemableStatuses")
    void redeem_rejectsNonIssuedCodeWithDistinctError(LicenseCodeStatus status, HttpStatus httpStatus,
            String errorCode) {
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        if (status == LicenseCodeStatus.REVOKED) {
            code.revoke("test");
        } else if (status == LicenseCodeStatus.REDEEMED) {
            code.markRedeemed(UUID.randomUUID(), Instant.now());
        } else if (status == LicenseCodeStatus.EXPIRED) {
            code.expire();
        }
        when(licenseCodeRepository.markRedeemed(any(), any(), any(), any(), any())).thenReturn(0);
        when(licenseCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.redeem(code.getCode(), hostAdmin(UUID.randomUUID())))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(httpStatus);
                    assertThat(ex.getMessage()).isEqualTo(errorCode);
                });
        verify(subscriptionActivationService, never()).activate(any(), any(), any());
    }

    @Test
    void revoke_redeemedCodeCancelsLinkedSubscription() {
        UUID tenantId = UUID.randomUUID();
        UUID subscriptionId = UUID.randomUUID();
        Subscription subscription = new Subscription();
        subscription.setPublicId(subscriptionId);
        UUID planId = UUID.randomUUID();
        LicenseCode code = LicenseCode.issue("ABCD-EFGH-JKLM-NPQR-STUV", planId,
                UUID.randomUUID(), Instant.now(), null);
        code.setPublicId(UUID.randomUUID());
        code.markRedeemed(tenantId, Instant.now());
        code.linkSubscription(subscriptionId);
        subscription.setTenantId(tenantId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(licenseCodeRepository.findWithLockByPublicId(code.getPublicId())).thenReturn(Optional.of(code));
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(activePlan(planId)));
        when(subscriptionRepository.findWithLockByPublicIdAndTenantId(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));
        when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);
        stubImpact(subscriptionId, tenantId);

        CurrentUser admin = platformAdmin();
        LicenseRevokePreviewResponse preview = service.previewRevoke(code.getPublicId(), admin);
        LicenseRevokeResponse response = service.revoke(code.getPublicId(), new ConfirmLicenseRevokeRequest(
                "fraud review", preview.scopeDigest(), preview.previewExpiresAt(), preview.effectiveState(),
                preview.planId(), preview.subscriptionPublicId(), preview.subscriptionStatus(), true, true, true),
                admin);

        assertThat(response.status()).isEqualTo(LicenseCodeStatus.REVOKED.name());
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).saveAndFlush(subscription);
        ArgumentCaptor<SubscriptionRevokedEvent> eventCaptor = ArgumentCaptor.forClass(SubscriptionRevokedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().subscriptionPublicId()).isEqualTo(subscriptionId);
        assertThat(eventCaptor.getValue().tenantPublicId()).isEqualTo(tenantId);
        assertThat(eventCaptor.getValue().planPublicId()).isEqualTo(planId);
        assertThat(eventCaptor.getValue().reason()).isEqualTo("fraud review");
    }

    @Test
    void revoke_issuedCodeDoesNotTouchSubscriptions() {
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        code.setPublicId(UUID.randomUUID());
        when(licenseCodeRepository.findWithLockByPublicId(code.getPublicId())).thenReturn(Optional.of(code));
        when(planRepository.findByPublicId(code.getPlanId())).thenReturn(Optional.of(activePlan(code.getPlanId())));
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        CurrentUser admin = platformAdmin();
        LicenseRevokePreviewResponse preview = service.previewRevoke(code.getPublicId(), admin);
        LicenseRevokeResponse response = service.revoke(code.getPublicId(), new ConfirmLicenseRevokeRequest(
                "manual cancellation", preview.scopeDigest(), preview.previewExpiresAt(), preview.effectiveState(),
                preview.planId(), null, null, false, true, true), admin);

        assertThat(response.status()).isEqualTo(LicenseCodeStatus.REVOKED.name());
        verifyNoSubscriptionCalls();
    }

    private static Stream<Arguments> nonRedeemableStatuses() {
        return Stream.of(
                Arguments.of(LicenseCodeStatus.REVOKED, HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_REVOKED),
                Arguments.of(LicenseCodeStatus.REDEEMED, HttpStatus.CONFLICT,
                        BillingConstants.LICENSE_CODE_ALREADY_REDEEMED),
                Arguments.of(LicenseCodeStatus.EXPIRED, HttpStatus.GONE, BillingConstants.LICENSE_CODE_EXPIRED));
    }

    private LicenseCode issuedCode(String value) {
        return LicenseCode.issue(value, UUID.randomUUID(), UUID.randomUUID(), Instant.now(), null);
    }

    private Plan activePlan(UUID planId) {
        Plan plan = new Plan();
        plan.setPublicId(planId);
        plan.setName("Exam");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(new BigDecimal("50000.00"));
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(25);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }

    private CurrentUser platformAdmin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    private CurrentUser platformManager() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_MANAGER"));
    }

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private void verifyNoSubscriptionCalls() {
        verify(subscriptionRepository, never()).findByPublicId(any());
        verify(subscriptionRepository, never()).findWithLockByPublicIdAndTenantId(any(), any());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }

    private void stubImpact(UUID subscriptionId, UUID tenantId) {
        org.mockito.Mockito.doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof SubscriptionRevocationImpactQuery query) {
                query.respond(SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact.empty(
                        subscriptionId, tenantId));
            }
            return null;
        }).when(eventPublisher).publishEvent(any(Object.class));
    }
}
