package com.pte.billing.internal.service.license;

import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Locks a code, loads what revoking it would touch and fingerprints that scope for the confirm step. */
class LicenseRevokeScopeResolver {

    private final LicenseCodeRepository licenseCodeRepository;
    private final ServicePlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    LicenseRevokeScopeResolver(LicenseCodeRepository licenseCodeRepository, ServicePlanRepository planRepository,
            SubscriptionRepository subscriptionRepository, ApplicationEventPublisher eventPublisher, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    LicenseCode lockedCode(UUID publicId) {
        if (publicId == null) {
            throw new LicenseCodeException(HttpStatus.NOT_FOUND, BillingConstants.LICENSE_CODE_NOT_FOUND);
        }
        return licenseCodeRepository.findWithLockByPublicId(publicId)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
    }

    RevokeScope loadRevokeScope(LicenseCode code) {
        LicenseCodeStatus effectiveState = LicenseCodeStateResolver.resolve(
                code, clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (code.getStatus() == LicenseCodeStatus.REVOKED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_ALREADY_REVOKED);
        }
        if (effectiveState == LicenseCodeStatus.EXPIRED) {
            throw new LicenseCodeException(HttpStatus.GONE, BillingConstants.LICENSE_CODE_EXPIRED);
        }

        ServicePlan plan = planRepository.findByPublicId(code.getPlanId())
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.CONFLICT,
                        BillingConstants.LICENSE_CODE_PLAN_REQUIRED));
        if (code.getStatus() == LicenseCodeStatus.ISSUED) {
            return new RevokeScope(code, effectiveState, null, null, "CODE_ONLY");
        }
        if (code.getStatus() != LicenseCodeStatus.REDEEMED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_NOT_REVOCABLE);
        }
        if (code.getSubscriptionId() == null
                || code.getRedeemedByTenantId() == null
                || plan.getType() != com.pte.billing.domain.enums.PlanType.EXAM_PACKAGE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT,
                    BillingConstants.LICENSE_CODE_REVOKE_CAPACITY_UNSUPPORTED);
        }

        Subscription subscription = subscriptionRepository
                .findWithLockByPublicIdAndTenantId(code.getSubscriptionId(), code.getRedeemedByTenantId())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.CONFLICT,
                        BillingConstants.LICENSE_CODE_SUBSCRIPTION_NOT_FOUND));
        if (!Objects.equals(subscription.getTenantId(), code.getRedeemedByTenantId())
                || !Objects.equals(subscription.getPlanId(), code.getPlanId())) {
            throw new LicenseCodeException(HttpStatus.CONFLICT,
                    BillingConstants.LICENSE_CODE_REVOKE_TENANT_MISMATCH);
        }

        SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact;
        try {
            SubscriptionRevocationImpactQuery query = new SubscriptionRevocationImpactQuery(
                    subscription.getPublicId(), subscription.getTenantId());
            eventPublisher.publishEvent(query);
            impact = query.requireResponse();
        } catch (LicenseCodeException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                    BillingConstants.LICENSE_CODE_REVOKE_IMPACT_UNAVAILABLE, ex);
        }
        return new RevokeScope(code, effectiveState, subscription, impact, "EXAM_SUBSCRIPTION");
    }

    String scopeDigest(RevokeScope scope, UUID actorId, Instant previewExpiresAt) {
        Subscription subscription = scope.subscription();
        SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact = scope.impact();
        String scheduled = impact == null ? "" : impact.scheduledSessionPublicIds().stream()
                .map(UUID::toString).sorted().collect(java.util.stream.Collectors.joining(","));
        String canonical = String.join("\n",
                "actor=" + actorId,
                "code=" + scope.code().getPublicId(),
                "plan=" + scope.code().getPlanId(),
                "state=" + scope.effectiveState(),
                "issuedAt=" + scope.code().getIssuedAt(),
                "expiresAt=" + scope.code().getCodeExpiresAt(),
                "subscription=" + (subscription == null ? null : subscription.getPublicId()),
                "subscriptionStatus=" + (subscription == null ? null : subscription.getStatus()),
                "tenant=" + (subscription == null ? null : subscription.getTenantId()),
                "scheduled=" + scheduled,
                "previewExpiresAt=" + previewExpiresAt);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    record RevokeScope(
            LicenseCode code,
            LicenseCodeStatus effectiveState,
            Subscription subscription,
            SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact,
            String impactCategory) {
    }
}
