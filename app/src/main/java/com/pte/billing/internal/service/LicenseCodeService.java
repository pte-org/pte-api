package com.pte.billing.internal.service;

import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.LicenseCodeResponse;
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

    @Autowired
    public LicenseCodeService(LicenseCodeRepository licenseCodeRepository, PlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            LicenseCodePersistenceService licenseCodePersistenceService,
            LicenseCodeGenerator licenseCodeGenerator,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.licenseCodePersistenceService = licenseCodePersistenceService;
        this.licenseCodeGenerator = licenseCodeGenerator;
        this.subscriptionActivationService = subscriptionActivationService;
        this.eventPublisher = eventPublisher;
    }

    public LicenseCodeResponse issue(UUID planPublicId, Instant codeExpiresAt, CurrentUser caller) {
        requirePlatformAdmin(caller);
        if (planPublicId == null) {
            throw invalid(BillingConstants.LICENSE_CODE_PLAN_REQUIRED);
        }
        Instant now = Instant.now();
        if (codeExpiresAt != null && !codeExpiresAt.isAfter(now)) {
            throw invalid(BillingConstants.LICENSE_CODE_EXPIRY_INVALID);
        }

        Plan plan = planRepository.findByPublicId(planPublicId)
                .orElseThrow(PlanNotFoundException::new);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_PLAN_NOT_ACTIVE);
        }

        for (int attempt = 1; attempt <= MAX_ISSUE_ATTEMPTS; attempt++) {
            String code = licenseCodeGenerator.generate();
            if (licenseCodeRepository.existsByCode(code)) {
                continue;
            }
            LicenseCode licenseCode = LicenseCode.issue(code, planPublicId, caller.userId(), now, codeExpiresAt);
            try {
                return LicenseCodeResponse.from(licenseCodePersistenceService.save(licenseCode));
            } catch (DataIntegrityViolationException ex) {
                if (attempt == MAX_ISSUE_ATTEMPTS) {
                    throw new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_GENERATION_FAILED, ex);
                }
            }
        }
        throw new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                BillingConstants.LICENSE_CODE_GENERATION_FAILED);
    }

    @Transactional(readOnly = true)
    public List<LicenseCodeResponse> list(CurrentUser caller) {
        requirePlatformAdmin(caller);
        return licenseCodeRepository.findTop100ByOrderByIssuedAtDesc().stream()
                .map(LicenseCodeResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionActivationResponse redeem(String rawCode, CurrentUser caller) {
        UUID tenantId = requireHostTenant(caller);
        String codeValue = normalizeCode(rawCode);
        Instant now = Instant.now();

        int updated = licenseCodeRepository.markRedeemed(codeValue, tenantId, now,
                LicenseCodeStatus.ISSUED, LicenseCodeStatus.REDEEMED);
        if (updated != 1) {
            throw explainRedeemFailure(codeValue, now);
        }

        LicenseCode licenseCode = licenseCodeRepository.findByCodeForUpdate(codeValue)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        Plan plan = planRepository.findByPublicId(licenseCode.getPlanId())
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
        if (licenseCode.getStatus() == LicenseCodeStatus.EXPIRED) {
            throw new LicenseCodeException(HttpStatus.CONFLICT, BillingConstants.LICENSE_CODE_NOT_REVOCABLE);
        }

        if (licenseCode.getStatus() == LicenseCodeStatus.REDEEMED
                && licenseCode.getSubscriptionId() != null) {
            Subscription subscription = subscriptionRepository.findByPublicId(licenseCode.getSubscriptionId())
                    .orElseThrow(() -> new LicenseCodeException(HttpStatus.INTERNAL_SERVER_ERROR,
                            BillingConstants.LICENSE_CODE_SUBSCRIPTION_NOT_FOUND));
            subscription.cancel();
            subscriptionRepository.saveAndFlush(subscription);
            eventPublisher.publishEvent(new SubscriptionRevokedEvent(subscription.getPublicId()));
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
        if (licenseCode.getStatus() == LicenseCodeStatus.EXPIRED
                || (licenseCode.getCodeExpiresAt() != null && !licenseCode.getCodeExpiresAt().isAfter(now))) {
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
