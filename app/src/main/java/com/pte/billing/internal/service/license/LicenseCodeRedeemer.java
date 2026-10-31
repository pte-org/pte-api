package com.pte.billing.internal.service.license;

import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.domain.LicenseCode;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.LicenseCodeStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.license.LicenseCodeException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.repository.LicenseCodeRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Claims a bearer for a tenant with a conditional update, then activates its plan through the shared path. */
class LicenseCodeRedeemer {

    private final LicenseCodeRepository licenseCodeRepository;
    private final ServicePlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionActivationService subscriptionActivationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    LicenseCodeRedeemer(LicenseCodeRepository licenseCodeRepository, ServicePlanRepository planRepository,
            SubscriptionRepository subscriptionRepository,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.licenseCodeRepository = licenseCodeRepository;
        this.planRepository = planRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionActivationService = subscriptionActivationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    SubscriptionActivationResponse redeem(String rawCode, UUID tenantId) {
        String codeValue = LicenseCodeAccessPolicy.normalizeCode(rawCode);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        int updated = licenseCodeRepository.markRedeemed(codeValue, tenantId, now,
                LicenseCodeStatus.ISSUED, LicenseCodeStatus.REDEEMED);
        if (updated != 1) {
            throw explainRedeemFailure(codeValue, tenantId, now);
        }

        LicenseCode licenseCode = licenseCodeRepository.findByCodeForUpdate(codeValue)
                .orElseThrow(() -> new LicenseCodeException(HttpStatus.NOT_FOUND,
                        BillingConstants.LICENSE_CODE_NOT_FOUND));
        ServicePlan plan = planRepository.findByPublicIdForUpdate(licenseCode.getPlanId())
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
                activation.grantedStudentSlots(),
                activation.startsAt()));
        return activation;
    }

    private LicenseCodeException explainRedeemFailure(String codeValue, UUID tenantId, Instant now) {
        LicenseCode licenseCode = licenseCodeRepository.findByCode(codeValue)
                // A code granted to another organization must not reveal that it exists.
                .filter(code -> code.getBoundTenantId() == null || code.getBoundTenantId().equals(tenantId))
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
}
