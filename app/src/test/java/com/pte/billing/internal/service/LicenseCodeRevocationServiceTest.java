package com.pte.billing.internal.service;

import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.LicenseIssueIntentRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.audit.AuditLogService;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LicenseCodeRevocationServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Mock private LicenseCodeRepository licenseCodeRepository;
    @Mock private PlanRepository planRepository;
    @Mock private SubscriptionRepository subscriptionRepository;
    @Mock private LicenseCodePersistenceService persistenceService;
    @Mock private LicenseCodeGenerator generator;
    @Mock private SubscriptionActivationService activationService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;
    @Mock private TenancyService tenancyService;

    private LicenseCodeService service;
    private CurrentUser admin;
    private UUID codeId;
    private UUID planId;
    private UUID subscriptionId;
    private UUID tenantId;
    private UUID sessionId;
    private LicenseCode code;
    private Subscription subscription;
    private Plan plan;

    @BeforeEach
    void setUp() {
        service = new LicenseCodeService(licenseCodeRepository, planRepository, subscriptionRepository,
                persistenceService, generator, activationService, eventPublisher,
                auditLogService, tenancyService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        admin = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        codeId = UUID.randomUUID();
        planId = UUID.randomUUID();
        subscriptionId = UUID.randomUUID();
        tenantId = UUID.randomUUID();
        sessionId = UUID.randomUUID();

        code = LicenseCode.issue("ABCD-EFGH-JKLM-NPQR-STUV", planId, admin.userId(),
                NOW.minusSeconds(60), null);
        code.setPublicId(codeId);
        code.markRedeemed(tenantId, NOW.minusSeconds(30));
        code.linkSubscription(subscriptionId);

        plan = new Plan();
        plan.setPublicId(planId);
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setName("Exam");
        plan.setPrice(BigDecimal.ONE);
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(25);

        subscription = new Subscription();
        subscription.setPublicId(subscriptionId);
        subscription.setTenantId(tenantId);
        subscription.setPlanId(planId);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        subscription.setLicenseKey("PTE-EXAM-KEY");
        subscription.setStartsAt(NOW.minusSeconds(60));
        subscription.setExpiresAt(NOW.plusSeconds(3600));
        subscription.setMaxStudentsPerSession(25);

        when(licenseCodeRepository.findWithLockByPublicId(codeId)).thenReturn(Optional.of(code));
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));
        lenient().when(subscriptionRepository.findWithLockByPublicIdAndTenantId(subscriptionId, tenantId))
                .thenReturn(Optional.of(subscription));
        lenient().when(licenseCodeRepository.saveAndFlush(any(LicenseCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(subscriptionRepository.saveAndFlush(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        answerImpact(List.of(sessionId), 0, 0);
    }

    @Test
    void previewAndConfirmUseTheSameScopeAndCancelLinkedSubscription() {
        LicenseRevokePreviewResponse preview = service.previewRevoke(codeId, admin);

        LicenseRevokeResponse result = service.revoke(codeId, confirm(preview), admin);

        assertThat(result.status()).isEqualTo(LicenseCodeStatus.REVOKED.name());
        assertThat(result.scheduledCancelledCount()).isEqualTo(1);
        assertThat(result.cancelledSessionPublicIds()).containsExactly(sessionId);
        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REVOKED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.CANCELLED);
        verify(eventPublisher).publishEvent(any(com.pte.billing.SubscriptionRevokedEvent.class));
    }

    @Test
    void changedMembershipRejectsConfirmationBeforeMutation() {
        LicenseRevokePreviewResponse preview = service.previewRevoke(codeId, admin);
        answerImpact(List.of(sessionId, UUID.randomUUID()), 0, 0);

        assertThatThrownBy(() -> service.revoke(codeId, confirm(preview), admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_REVOKE_SCOPE_CHANGED);
                });

        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REDEEMED);
        assertThat(subscription.getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(licenseCodeRepository, never()).saveAndFlush(code);
        verify(subscriptionRepository, never()).saveAndFlush(subscription);
    }

    @Test
    void expiredPreviewCannotAuthorizeMutation() {
        LicenseRevokePreviewResponse preview = service.previewRevoke(codeId, admin);
        ConfirmLicenseRevokeRequest expired = new ConfirmLicenseRevokeRequest(
                "fraud review", preview.scopeDigest(), NOW.minusSeconds(1),
                preview.effectiveState(), preview.planId(), preview.subscriptionPublicId(),
                preview.subscriptionStatus(), true, true, true);

        assertThatThrownBy(() -> service.revoke(codeId, expired, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_REVOKE_PREVIEW_EXPIRED);
                });
        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REDEEMED);
    }

    @Test
    void redeemedCapacityCodeIsUnsupportedAndRemainsUnchanged() {
        code = LicenseCode.issue("CAPACITY-CODE", planId, admin.userId(), NOW.minusSeconds(60), null);
        code.setPublicId(codeId);
        code.markRedeemed(tenantId, NOW.minusSeconds(30));
        plan.setType(PlanType.STUDENT_CAPACITY);
        when(licenseCodeRepository.findWithLockByPublicId(codeId)).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service.previewRevoke(codeId, admin))
                .isInstanceOfSatisfying(LicenseCodeException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.LICENSE_CODE_REVOKE_CAPACITY_UNSUPPORTED);
                });
        assertThat(code.getStatus()).isEqualTo(LicenseCodeStatus.REDEEMED);
    }

    @Test
    void issuedExamCodeCanBeConfirmedWithoutSubscriptionAcknowledgement() {
        code = LicenseCode.issue("ISSUED-CODE", planId, admin.userId(), NOW.minusSeconds(60), null);
        code.setPublicId(codeId);
        when(licenseCodeRepository.findWithLockByPublicId(codeId)).thenReturn(Optional.of(code));

        LicenseRevokePreviewResponse preview = service.previewRevoke(codeId, admin);
        LicenseRevokeResponse result = service.revoke(codeId,
                new ConfirmLicenseRevokeRequest("manual cancellation", preview.scopeDigest(),
                        preview.previewExpiresAt(), preview.effectiveState(), preview.planId(),
                        null, null, false, true, true), admin);

        assertThat(result.status()).isEqualTo(LicenseCodeStatus.REVOKED.name());
        assertThat(result.subscriptionPublicId()).isNull();
        verify(eventPublisher, never()).publishEvent(any(com.pte.billing.SubscriptionRevokedEvent.class));
    }

    private ConfirmLicenseRevokeRequest confirm(LicenseRevokePreviewResponse preview) {
        return new ConfirmLicenseRevokeRequest("fraud review", preview.scopeDigest(),
                preview.previewExpiresAt(), preview.effectiveState(), preview.planId(),
                preview.subscriptionPublicId(), preview.subscriptionStatus(), true, true, true);
    }

    private void answerImpact(List<UUID> scheduled, int open, int closed) {
        lenient().doAnswer(invocation -> {
            Object event = invocation.getArgument(0);
            if (event instanceof SubscriptionRevocationImpactQuery query) {
                query.respond(new SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact(
                        subscriptionId, tenantId, scheduled, scheduled.size(), open, closed));
            }
            return null;
        }).when(eventPublisher).publishEvent(any(Object.class));
    }
}
