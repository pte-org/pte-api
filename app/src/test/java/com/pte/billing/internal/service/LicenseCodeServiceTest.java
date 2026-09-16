package com.pte.billing.internal.service;

import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.LicenseCodeResponse;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

    private LicenseCodeService service;

    @BeforeEach
    void setUp() {
        service = new LicenseCodeService(licenseCodeRepository, planRepository, subscriptionRepository,
                licenseCodePersistenceService, licenseCodeGenerator, subscriptionActivationService);
    }

    @Test
    void issue_returnsGeneratedCodeForActivePlan() {
        UUID planId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant expiry = Instant.now().plusSeconds(3600);
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(activePlan(planId)));
        when(licenseCodeGenerator.generate()).thenReturn("ABCD-EFGH-JKLM-NPQR-STUV");
        when(licenseCodePersistenceService.save(any(LicenseCode.class))).thenAnswer(invocation -> {
            LicenseCode code = invocation.getArgument(0);
            code.setPublicId(UUID.randomUUID());
            return code;
        });

        LicenseCodeResponse response = service.issue(planId, expiry,
                new CurrentUser(adminId, null, List.of("PLATFORM_ADMIN")));

        assertThat(response.code()).isEqualTo("ABCD-EFGH-JKLM-NPQR-STUV");
        assertThat(response.planId()).isEqualTo(planId);
        assertThat(response.issuedBy()).isEqualTo(adminId);
        assertThat(response.status()).isEqualTo(LicenseCodeStatus.ISSUED.name());
    }

    @Test
    void issue_rejectsInactivePlan() {
        UUID planId = UUID.randomUUID();
        Plan plan = activePlan(planId);
        plan.setStatus(PlanStatus.ARCHIVED);
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.issue(planId, null, platformAdmin()))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
                });
        verify(licenseCodeGenerator, never()).generate();
    }

    @Test
    void redeem_marksCodeAtomicallyAndActivatesOnce() {
        UUID tenantId = UUID.randomUUID();
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        SubscriptionActivationResponse activation = SubscriptionActivationResponse.fromSubscription(
                tenantId, code.getPlanId(), "PTE-EXAM-2026-ABC123", Instant.now(),
                Instant.now().plusSeconds(3600), 25, "ACTIVE", ActivationSource.LICENSE_CODE.name());
        when(licenseCodeRepository.markRedeemed(eq(code.getCode()), eq(tenantId), any(),
                eq(LicenseCodeStatus.ISSUED), eq(LicenseCodeStatus.REDEEMED)))
                .thenAnswer(invocation -> {
                    code.markRedeemed(tenantId, invocation.getArgument(2));
                    return 1;
                });
        when(licenseCodeRepository.findByCodeForUpdate(code.getCode())).thenReturn(Optional.of(code));
        when(planRepository.findByPublicId(code.getPlanId())).thenReturn(Optional.of(activePlan(code.getPlanId())));
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
    }

    @Test
    void redeem_sameCodeTwiceActivatesOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        Subscription subscription = new Subscription();
        subscription.setPublicId(UUID.randomUUID());
        SubscriptionActivationResponse activation = SubscriptionActivationResponse.fromSubscription(
                tenantId, code.getPlanId(), "PTE-EXAM-2026-ABC123", Instant.now(),
                Instant.now().plusSeconds(3600), 25, "ACTIVE", ActivationSource.LICENSE_CODE.name());
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
        when(planRepository.findByPublicId(code.getPlanId())).thenReturn(Optional.of(activePlan(code.getPlanId())));
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
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        code.markRedeemed(UUID.randomUUID(), Instant.now());
        UUID subscriptionId = UUID.randomUUID();
        code.linkSubscription(subscriptionId);
        Subscription subscription = new Subscription();
        subscription.setPublicId(subscriptionId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        when(licenseCodeRepository.findByCodeForUpdate(code.getCode())).thenReturn(Optional.of(code));
        when(subscriptionRepository.findByPublicId(subscriptionId)).thenReturn(Optional.of(subscription));
        when(subscriptionRepository.saveAndFlush(subscription)).thenReturn(subscription);
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        LicenseCodeResponse response = service.revoke(code.getCode(), "fraud review", platformAdmin());

        assertThat(response.status()).isEqualTo(LicenseCodeStatus.REVOKED.name());
        assertThat(response.revokeReason()).isEqualTo("fraud review");
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(subscriptionRepository).saveAndFlush(subscription);
    }

    @Test
    void revoke_issuedCodeDoesNotTouchSubscriptions() {
        LicenseCode code = issuedCode("ABCD-EFGH-JKLM-NPQR-STUV");
        when(licenseCodeRepository.findByCodeForUpdate(code.getCode())).thenReturn(Optional.of(code));
        when(licenseCodeRepository.saveAndFlush(code)).thenReturn(code);

        service.revoke(code.getCode(), "manual cancellation", platformAdmin());

        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REVOKED);
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

    private CurrentUser hostAdmin(UUID tenantId) {
        return new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
    }

    private void verifyNoSubscriptionCalls() {
        verify(subscriptionRepository, never()).findByPublicId(any());
        verify(subscriptionRepository, never()).saveAndFlush(any(Subscription.class));
    }
}
