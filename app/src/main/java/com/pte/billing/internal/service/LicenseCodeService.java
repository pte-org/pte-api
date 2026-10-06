package com.pte.billing.internal.service;

import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.billing.SubscriptionRevocationImpactQuery;
import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.AdminLicenseCodeSummary;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.LicenseCodeRevealResponse;
import com.pte.billing.internal.dto.request.ConfirmLicenseRevokeRequest;
import com.pte.billing.internal.dto.response.LicenseRevokePreviewResponse;
import com.pte.billing.internal.dto.response.LicenseRevokeResponse;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import com.pte.tenancy.TenancyService;
import com.pte.tenancy.TenantSummary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Issues bearer codes and funnels redemption through the single activation path. */
@Service
public class LicenseCodeService {

    private static final int MAX_ISSUE_ATTEMPTS = 5;
    private static final int DEFAULT_ADMIN_PAGE_SIZE = 25;
    private static final int MAX_ADMIN_PAGE_SIZE = 100;
    private static final Duration REVOKE_PREVIEW_TTL = Duration.ofMinutes(5);

    private final LicenseCodeRepository licenseCodeRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LicenseCodePersistenceService licenseCodePersistenceService;
    private final LicenseCodeGenerator licenseCodeGenerator;
    private final SubscriptionActivationService subscriptionActivationService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final TenancyService tenancyService;
    private final Clock clock;

    @Autowired
    public LicenseCodeService(LicenseCodeRepository licenseCodeRepository, PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            LicenseCodePersistenceService licenseCodePersistenceService,
            LicenseCodeGenerator licenseCodeGenerator,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher, AuditLogService auditLogService,
            TenancyService tenancyService, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.licenseCodePersistenceService = licenseCodePersistenceService;
        this.licenseCodeGenerator = licenseCodeGenerator;
        this.subscriptionActivationService = subscriptionActivationService;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
        this.tenancyService = tenancyService;
        this.clock = clock;
    }

    public LicenseIssueReceipt issue(UUID planPublicId, Instant codeExpiresAt, UUID key, CurrentUser caller) {
        requirePlatformAdmin(caller);
        if (planPublicId == null) {
            throw invalid(BillingConstants.LICENSE_CODE_PLAN_REQUIRED);
        }
        if (key == null) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_IDEMPOTENCY_KEY_REQUIRED);
        }
        if (codeExpiresAt != null && codeExpiresAt.getNano() % 1000 != 0) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_EXPIRY_PRECISION_INVALID);
        }
        String fingerprint = fingerprint(planPublicId, codeExpiresAt);
        var prior = licenseCodePersistenceService.replay(caller.userId(), key, fingerprint);
        if (prior.isPresent()) return prior.get();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            String code = licenseCodeGenerator.generate();
            LicenseCode licenseCode = LicenseCode.issue(code, planPublicId, caller.userId(), now, codeExpiresAt);
            try {
                return licenseCodePersistenceService.issue(licenseCode, key, fingerprint);
            } catch (DataIntegrityViolationException ex) {
                if (constraint(ex, BillingConstants.LICENSE_ISSUE_INTENT_CONSTRAINT)) {
                    var winner = licenseCodePersistenceService.replay(caller.userId(), key, fingerprint);
                    if (winner.isPresent()) return winner.get();
                } else if (!constraint(ex, "license_codes_code_key") && !constraint(ex, "uk_license_codes_code")) {
                    throw new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_ISSUE_FAILED, ex);
                }
            }
        }
        throw new LicenseCodeException(HttpStatus.SERVICE_UNAVAILABLE, BillingConstants.LICENSE_CODE_ISSUE_RETRYABLE);
    }

    private String fingerprint(UUID planId, Instant expiry) {
        try {
            String canonical = planId + "\n" + (expiry == null ? "null" : expiry.toString());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private boolean constraint(Throwable failure, String name) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && name.equals(violation.getConstraintName())) return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminLicenseCodeSummary> listForAdmin(Integer requestedPage, Integer requestedSize,
            String requestedStatus, UUID planId, UUID tenantId, CurrentUser caller) {
        requirePlatformAdmin(caller);
        int page = requestedPage == null ? 0 : requestedPage;
        int size = requestedSize == null ? DEFAULT_ADMIN_PAGE_SIZE : requestedSize;
        if (page < 0) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_PAGE_INVALID);
        }
        if (size < 1 || size > MAX_ADMIN_PAGE_SIZE) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_PAGE_SIZE_INVALID);
        }
        LicenseCodeStatus status = parseAdminStatus(requestedStatus);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Page<LicenseCode> result = licenseCodeRepository.findAdminPage(status, planId, tenantId, now,
                PageRequest.of(page, size));
        return new PagedResult<>(adminSummaries(result.getContent(), now),
                new PageMeta(result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(),
                        result.isFirst(), result.isLast(), result.hasNext(), result.hasPrevious()));
    }

    @Transactional(readOnly = true)
    public AdminLicenseCodeSummary getForAdmin(UUID publicId, CurrentUser caller) {
        requirePlatformAdmin(caller);
        LicenseCode code = licenseCodeRepository.findByPublicId(publicId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        return adminSummaries(List.of(code), clock.instant().truncatedTo(ChronoUnit.MICROS)).getFirst();
    }

    @Transactional(readOnly = true)
    public AdminLicenseCodeSummary lookupForAdmin(String rawCode, CurrentUser caller) {
        requirePlatformAdmin(caller);
        String codeValue = normalizeCode(rawCode);
        LicenseCode code = licenseCodeRepository.findByCode(codeValue)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_LOOKUP_NOT_FOUND));
        return adminSummaries(List.of(code), clock.instant().truncatedTo(ChronoUnit.MICROS)).getFirst();
    }

    /** Returns the bearer only after the caller has passed the platform-admin guard again. */
    @Transactional
    public LicenseCodeRevealResponse revealForAdmin(UUID publicId, CurrentUser caller) {
        requirePlatformAdmin(caller);
        LicenseCode code = licenseCodeRepository.findByPublicId(publicId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        auditLogService.record(caller, "LicenseCode", publicId.toString(),
                BillingConstants.LICENSE_CODE_REVEAL_AUDIT,
                BillingConstants.LICENSE_CODE_REVEAL_AUDIT_SUMMARY);
        return new LicenseCodeRevealResponse(code.getCode());
    }

    /** Authorizes the controlled retirement response for the old raw-list route. */
    public void ensureLegacyListAccess(CurrentUser caller) {
        requirePlatformAdmin(caller);
    }

    private LicenseCodeStatus parseAdminStatus(String rawStatus) {
        if (rawStatus == null) {
            return null;
        }
        if (rawStatus.isBlank()) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_STATUS_INVALID);
        }
        try {
            return LicenseCodeStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST, BillingConstants.LICENSE_CODE_STATUS_INVALID);
        }
    }

    private List<AdminLicenseCodeSummary> adminSummaries(List<LicenseCode> codes, Instant now) {
        if (codes.isEmpty()) {
            return List.of();
        }
        Set<UUID> planIds = codes.stream().map(LicenseCode::getPlanId).collect(Collectors.toSet());
        Map<UUID, Plan> plans = planIds.isEmpty() ? Map.of() : planRepository.findByPublicIdIn(planIds).stream()
                .collect(Collectors.toUnmodifiableMap(Plan::getPublicId, Function.identity()));
        Set<UUID> tenantIds = codes.stream().map(LicenseCode::getRedeemedByTenantId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, TenantSummary> recipients = tenancyService.findTenantSummaries(tenantIds);
        return codes.stream()
                .map(code -> AdminLicenseCodeSummary.from(code,
                        LicenseCodeStateResolver.resolve(code, now),
                        plans.get(code.getPlanId()), recipients.get(code.getRedeemedByTenantId())))
                .toList();
    }

    @Transactional
    public LicenseRevokePreviewResponse previewRevoke(UUID publicId, CurrentUser caller) {
        requirePlatformAdmin(caller);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LicenseCode code = lockedCode(publicId);
        RevokeScope scope = loadRevokeScope(code);
        Instant previewExpiresAt = now.plus(REVOKE_PREVIEW_TTL);
        return toPreview(scope, caller.userId(), previewExpiresAt);
    }

    @Transactional
    public LicenseRevokeResponse revoke(UUID publicId, ConfirmLicenseRevokeRequest request,
            CurrentUser caller) {
        requirePlatformAdmin(caller);
        if (request == null) {
            throw new LicenseCodeException(HttpStatus.BAD_REQUEST,
                    BillingConstants.LICENSE_CODE_REVOKE_CONFIRMATION_REQUIRED);
        }
        String reason = validateRevokeReason(request.reason());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        LicenseCode code = lockedCode(publicId);
        RevokeScope scope = loadRevokeScope(code);
        validatePreview(request, scope, caller.userId(), now);
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

    /** Rejects the legacy bearer-token mutation route without echoing its path value. */
    public void rejectLegacyRevoke(CurrentUser caller) {
        requirePlatformAdmin(caller);
        throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_REVOKE_LEGACY_ENDPOINT);
    }

    @Transactional
    public SubscriptionActivationResponse redeem(String rawCode, CurrentUser caller) {
        UUID tenantId = requireHostTenant(caller);
        String codeValue = normalizeCode(rawCode);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        int updated = licenseCodeRepository.markRedeemed(codeValue, tenantId, now,
                LicenseCodeStatus.ISSUED, LicenseCodeStatus.REDEEMED);
        if (updated != 1) {
            throw explainRedeemFailure(codeValue, now);
        }

        LicenseCode licenseCode = licenseCodeRepository.findByCodeForUpdate(codeValue)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        Plan plan = planRepository.findByPublicIdForUpdate(licenseCode.getPlanId())
                .filter(p -> !p.isDeleted())
                .orElseThrow(PlanNotFoundException::new);
        SubscriptionActivationResponse activation = subscriptionActivationService.activate(
                tenantId, plan, ActivationSource.LICENSE_CODE);

        if (activation.kind() == SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION) {
            UUID subscriptionId = subscriptionRepository.findByLicenseKey(activation.licenseKey())
                    .map(Subscription::getPublicId)
                    .orElseThrow(() -> new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_SUBSCRIPTION_NOT_FOUND));
            licenseCode.linkSubscription(subscriptionId);
        }
        licenseCodeRepository.saveAndFlush(licenseCode);
        eventPublisher.publishEvent(new CommercialOutcomeConfirmedEvent(
                tenantId,
                plan.getPublicId(),
                activation.kind() == SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION
                        ? CommercialOutcomeType.EXAM_PACKAGE : CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.LICENSE_CODE,
                activation.kind() == SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION
                        ? CommercialActivationTarget.SUBSCRIPTION : CommercialActivationTarget.QUOTA,
                activation.targetPublicId(),
                activation.grantedStudentSlots()));
        return activation;
    }

    private LicenseCode lockedCode(UUID publicId) {
        if (publicId == null) {
            throw new LicenseCodeException(HttpStatus.NOT_FOUND, BillingConstants.LICENSE_CODE_NOT_FOUND);
        }
        return licenseCodeRepository.findWithLockByPublicId(publicId)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
    }

    private RevokeScope loadRevokeScope(LicenseCode code) {
        LicenseCodeStatus effectiveState = LicenseCodeStateResolver.resolve(
                code, clock.instant().truncatedTo(ChronoUnit.MICROS));
        if (code.getStatus() == LicenseCodeStatus.REVOKED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_ALREADY_REVOKED);
        }
        if (effectiveState == LicenseCodeStatus.EXPIRED) {
            throw new LicenseCodeException(HttpStatus.GONE, BillingConstants.LICENSE_CODE_EXPIRED);
        }

        Plan plan = planRepository.findByPublicId(code.getPlanId())
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

    private LicenseRevokePreviewResponse toPreview(RevokeScope scope, UUID actorId,
            Instant previewExpiresAt) {
        Subscription subscription = scope.subscription();
        SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact = scope.impact();
        String digest = scopeDigest(scope, actorId, previewExpiresAt);
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
                        scopeDigest(scope, actorId, expiresAt).getBytes(StandardCharsets.UTF_8))) {
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
            throw invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_REQUIRED);
        }
        String normalized = reason.trim();
        if (normalized.length() > 255) {
            throw invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_MAX);
        }
        return normalized;
    }

    private String scopeDigest(RevokeScope scope, UUID actorId, Instant previewExpiresAt) {
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

    private record RevokeScope(
            LicenseCode code,
            LicenseCodeStatus effectiveState,
            Subscription subscription,
            SubscriptionRevocationImpactQuery.SubscriptionRevocationImpact impact,
            String impactCategory) {
    }

    private LicenseCodeException explainRedeemFailure(String codeValue, Instant now) {
        LicenseCode licenseCode = licenseCodeRepository.findByCode(codeValue)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        if (licenseCode.getStatus() == LicenseCodeStatus.REVOKED) {
            return new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_REVOKED);
        }
        if (licenseCode.getStatus() == LicenseCodeStatus.REDEEMED) {
            return new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_ALREADY_REDEEMED);
        }
        if (LicenseCodeStateResolver.resolve(licenseCode, now) == LicenseCodeStatus.EXPIRED) {
            return new LicenseCodeException(HttpStatus.GONE, BillingConstants.LICENSE_CODE_EXPIRED);
        }
        return new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_NOT_REDEEMABLE);
    }

    private UUID requireHostTenant(CurrentUser caller) {
        if (caller == null || caller.userId() == null || !caller.hasRole("HOST_ADMIN")) {
            throw new LicenseCodeException(HttpStatus.FORBIDDEN, BillingConstants.LICENSE_CODE_HOST_ADMIN_REQUIRED);
        }
        if (caller.tenantId() == null) {
            throw invalid(BillingConstants.LICENSE_CODE_TENANT_REQUIRED);
        }
        return caller.tenantId();
    }

    private void requirePlatformAdmin(CurrentUser caller) {
        if (caller == null || caller.userId() == null || !caller.isPlatformUser()
                || !caller.hasRole("PLATFORM_ADMIN")) {
            throw new LicenseCodeException(HttpStatus.FORBIDDEN, BillingConstants.LICENSE_CODE_PLATFORM_ADMIN_REQUIRED);
        }
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw invalid(BillingConstants.LICENSE_CODE_REQUIRED);
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    private LicenseCodeException invalid(String code) {
        return new LicenseCodeException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
