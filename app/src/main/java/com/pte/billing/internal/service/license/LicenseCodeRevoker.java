package com.pte.billing.internal.service.license;

import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Subscription;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.request.license.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.service.license.LicenseRevokeScopeResolver.RevokeScope;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Two-step revoke: a short-lived preview of the impact, then a confirm that must match that preview. */
class LicenseCodeRevoker {

    private static final Duration REVOKE_PREVIEW_TTL = Duration.ofMinutes(5);

    private final LicenseCodeRepository licenseCodeRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final LicenseRevokeScopeResolver scopeResolver;
    private final Clock clock;

    LicenseCodeRevoker(LicenseCodeRepository licenseCodeRepository, SubscriptionRepository subscriptionRepository,
            ApplicationEventPublisher eventPublisher, LicenseRevokeScopeResolver scopeResolver, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.eventPublisher = eventPublisher;
        this.scopeResolver = scopeResolver;
        this.clock = clock;
    }

    LicenseRevokePreviewResponse previewRevoke(UUID publicId, UUID actorId) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LicenseCode code = scopeResolver.lockedCode(publicId);
        RevokeScope scope = scopeResolver.loadRevokeScope(code);
        Instant previewExpiresAt = now.plus(REVOKE_PREVIEW_TTL);
        return toPreview(scope, actorId, previewExpiresAt);
    }

    LicenseRevokeResponse revoke(UUID publicId, ConfirmLicenseRevokeRequest request, UUID actorId) {
        if (request == null) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST,
                    BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED);
        }
        String reason = validateRevokeReason(request.reason());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LicenseCode code = scopeResolver.lockedCode(publicId);
        RevokeScope scope = scopeResolver.loadRevokeScope(code);
        validatePreview(request, scope, actorId, now);
        validateAcknowledgements(request, scope);

        code.revoke(reason);
        Subscription subscription = scope.subscription();
        boolean subscriptionCancelled = subscription != null
                && subscription.getStatus() == com.pte.billing.domain.enums.SubscriptionStatus.ACTIVE;
        if (subscription != null) {
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
        }
        licenseCodeRepository.saveAndFlush(code);
        if (subscription != null) {
            eventPublisher.publishEvent(new SubscriptionRevokedEvent(
                    subscription.getPublicId(),
                    subscription.getTenantId(),
                    subscription.getPlanId(),
                    reason));
        }
        SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact = scope.impact();
        return new LicenseRevokeResponse(
                code.getPublicId(),
                code.getStatus().name(),
                scope.impactCategory(),
                subscription == null ? null : subscription.getPublicId(),
                subscription == null ? null : subscription.getStatus().name(),
                subscriptionCancelled,
                impact == null ? 0 : impact.scheduledCount(),
                impact == null ? 0 : impact.openCount(),
                impact == null ? 0 : impact.closedCount(),
                impact == null ? List.of() : impact.scheduledSessionPublicIds());
    }

    private LicenseRevokePreviewResponse toPreview(RevokeScope scope, UUID actorId,
            Instant previewExpiresAt) {
        Subscription subscription = scope.subscription();
        SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact = scope.impact();
        String digest = scopeResolver.scopeDigest(scope, actorId, previewExpiresAt);
        return new LicenseRevokePreviewResponse(
                scope.code().getPublicId(),
                scope.code().getPlanId(),
                scope.effectiveState().name(),
                scope.impactCategory(),
                subscription == null ? null : subscription.getPublicId(),
                subscription == null ? null : subscription.getStatus().name(),
                subscription == null ? null : subscription.getTenantId(),
                impact == null ? 0 : impact.scheduledCount(),
                impact == null ? 0 : impact.openCount(),
                impact == null ? 0 : impact.closedCount(),
                impact == null ? List.of() : impact.scheduledSessionPublicIds(),
                previewExpiresAt,
                digest);
    }

    private void validatePreview(ConfirmLicenseRevokeRequest request, RevokeScope scope,
            UUID actorId, Instant now) {
        Instant expiresAt = request.previewExpiresAt();
        if (expiresAt == null || request.scopeDigest() == null || request.scopeDigest().isBlank()
                || request.expectedEffectiveState() == null || request.expectedPlanId() == null
                || request.cancelSubscription() == null || request.cancelScheduledScope() == null
                || request.preserveOpenClosed() == null) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST,
                    BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED);
        }
        if (!expiresAt.isAfter(now) || expiresAt.isAfter(now.plus(REVOKE_PREVIEW_TTL))) {
            throw new LicenseCodeException(HttpStatus.CONFLICT,
                    BillingConstants.LICENSE_CODE_REVOKE_PREVIEW_EXPIRED);
        }
        Subscription subscription = scope.subscription();
        String actualSubscriptionStatus = subscription == null ? null : subscription.getStatus().name();
        if (!Objects.equals(request.expectedEffectiveState(), scope.effectiveState().name())
                || !Objects.equals(request.expectedPlanId(), scope.code().getPlanId())
                || !Objects.equals(request.expectedSubscriptionPublicId(),
                        subscription == null ? null : subscription.getPublicId())
                || !Objects.equals(request.expectedSubscriptionStatus(), actualSubscriptionStatus)
                || !MessageDigest.isEqual(
                        request.scopeDigest().getBytes(StandardCharsets.UTF_8),
                        scopeResolver.scopeDigest(scope, actorId, expiresAt).getBytes(StandardCharsets.UTF_8))) {
            throw new LicenseCodeException(HttpStatus.CONFLICT,
                    BillingConstants.LICENSE_CODE_REVOKE_SCOPE_CHANGED);
        }
    }

    private void validateAcknowledgements(ConfirmLicenseRevokeRequest request, RevokeScope scope) {
        boolean hasSubscription = scope.subscription() != null;
        if (!Boolean.TRUE.equals(request.cancelScheduledScope())
                || !Boolean.TRUE.equals(request.preserveOpenClosed())
                || !Objects.equals(Boolean.valueOf(hasSubscription), request.cancelSubscription())) {
            throw new LicenseCodeException(HttpStatus.UNPROCESSABLE_ENTITY,
                    BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED);
        }
    }

    private String validateRevokeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw LicenseCodeAccessPolicy.invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_REQUIRED);
        }
        String normalized = reason.trim();
        if (normalized.length() > 255) {
            throw LicenseCodeAccessPolicy.invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_MAX);
        }
        return normalized;
    }
}
