package com.pte.billing.internal.service;

import com.pte.billing.SubscriptionRevokedEvent;
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
import com.pte.billing.internal.dto.response.LicenseCodeResponse;
import com.pte.billing.internal.dto.response.LicenseIssueReceipt;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.LicenseCodeException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Issues bearer codes and funnels redemption through the single activation path. */
@Service
public class LicenseCodeService {

    private static final int MAX_ISSUE_ATTEMPTS = 5;

    private final LicenseCodeRepository licenseCodeRepository;
    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final LicenseCodePersistenceService licenseCodePersistenceService;
    private final LicenseCodeGenerator licenseCodeGenerator;
    private final SubscriptionActivationService subscriptionActivationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public LicenseCodeService(LicenseCodeRepository licenseCodeRepository, PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            LicenseCodePersistenceService licenseCodePersistenceService,
            LicenseCodeGenerator licenseCodeGenerator,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.licenseCodePersistenceService = licenseCodePersistenceService;
        this.licenseCodeGenerator = licenseCodeGenerator;
        this.subscriptionActivationService = subscriptionActivationService;
        this.eventPublisher = eventPublisher;
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
    public List<LicenseCodeResponse> list(CurrentUser caller) {
        requirePlatformAdmin(caller);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        return licenseCodeRepository.findTop100ByOrderByIssuedAtDesc().stream()
                .map(code -> LicenseCodeResponse.from(code, LicenseCodeStateResolver.resolve(code, now)))
                .toList();
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

    @Transactional
    public LicenseCodeResponse revoke(String rawCode, String reason, CurrentUser caller) {
        requirePlatformAdmin(caller);
        String codeValue = normalizeCode(rawCode);
        if (reason == null || reason.isBlank()) {
            throw invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_REQUIRED);
        }
        if (reason.trim().length() > 255) {
            throw invalid(BillingConstants.LICENSE_CODE_REVOKE_REASON_MAX);
        }

        LicenseCode licenseCode = licenseCodeRepository.findByCodeForUpdate(codeValue)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        if (licenseCode.getStatus() == LicenseCodeStatus.REVOKED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_ALREADY_REVOKED);
        }
        if (LicenseCodeStateResolver.resolve(licenseCode, clock.instant().truncatedTo(ChronoUnit.MICROS))
                == LicenseCodeStatus.EXPIRED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_NOT_REVOCABLE);
        }

        if (licenseCode.getStatus() == LicenseCodeStatus.REDEEMED
                && licenseCode.getSubscriptionId() != null) {
            Subscription subscription = subscriptionRepository.findByPublicId(licenseCode.getSubscriptionId())
                    .orElseThrow(() -> new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_SUBSCRIPTION_NOT_FOUND));
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
            eventPublisher.publishEvent(new SubscriptionRevokedEvent(
                    subscription.getPublicId(),
                    subscription.getTenantId(),
                    subscription.getPlanId(),
                    reason.trim()));
        }

        licenseCode.revoke(reason.trim());
        return LicenseCodeResponse.from(licenseCodeRepository.saveAndFlush(licenseCode));
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
