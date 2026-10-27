package com.pte.billing.internal.service.subscription;

import com.pte.billing.internal.service.license.LicenseKeyGenerator;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.Subscription;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.domain.enums.SubscriptionStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.SubscriptionActivationException;
import com.pte.billing.internal.repository.SubscriptionRepository;
import com.pte.tenancy.TenancyService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** The single write path that can create a Subscription. */
@Service
public class SubscriptionActivationService {

    private static final int MAX_LICENSE_KEY_PRECHECK_ATTEMPTS = 5;

    private final SubscriptionPersistenceService subscriptionPersistenceService;
    private final SubscriptionRepository subscriptionRepository;
    private final LicenseKeyGenerator licenseKeyGenerator;
    private final TenancyService tenancyService;
    private final Clock clock;

    public SubscriptionActivationService(SubscriptionPersistenceService subscriptionPersistenceService,
            SubscriptionRepository subscriptionRepository, LicenseKeyGenerator licenseKeyGenerator,
            TenancyService tenancyService,
            Clock clock) {
        this.subscriptionPersistenceService = subscriptionPersistenceService;
        this.subscriptionRepository = subscriptionRepository;
        this.licenseKeyGenerator = licenseKeyGenerator;
        this.tenancyService = tenancyService;
        this.clock = clock;
    }

    /**
     * Payment webhooks and license-code redemption must both call this method.
     * STUDENT_CAPACITY intentionally bypasses Subscription and appends to the
     * tenancy quota ledger instead.
     */
    @Transactional
    public SubscriptionActivationResponse activate(UUID tenantId, ServicePlan plan, ActivationSource source) {
        validateRequest(tenantId, plan, source);

        if (plan.getType() == PlanType.STUDENT_CAPACITY) {
            int slots = plan.getExtraStudentSlots();
            UUID quotaGrantId = tenancyService.grantQuota(tenantId, slots,
                    "Activated STUDENT_CAPACITY plan " + plan.getPublicId());
            return SubscriptionActivationResponse.forStudentCapacity(tenantId, plan.getPublicId(), slots,
                    quotaGrantId);
        }

        Instant startsAt = clock.instant();
        Subscription subscription = saveWithUniqueLicenseKey(tenantId, plan, source, startsAt);
        return SubscriptionActivationResponse.fromSubscription(
                subscription.getTenantId(),
                subscription.getPlanId(),
                subscription.getLicenseKey(),
                subscription.getStartsAt(),
                subscription.getExpiresAt(),
                subscription.getMaxStudentsPerSession(),
                subscription.getStatus().name(),
                subscription.getActivationSource().name(),
                subscription.getPublicId());
    }

    private Subscription saveWithUniqueLicenseKey(UUID tenantId, ServicePlan plan, ActivationSource source,
            Instant startsAt) {
        Instant expiresAt = calculateExpiryAfterRenewal(plan, startsAt);
        // The pre-check can discard a generated key without touching the DB.
        // A uniqueness race during save aborts this whole transaction; retrying
        // only the repository call would leave the caller's transition rollback-only.
        for (int attempt = 1; attempt <= MAX_LICENSE_KEY_PRECHECK_ATTEMPTS; attempt++) {
            String licenseKey = licenseKeyGenerator.generate(plan, startsAt);
            if (subscriptionRepository.existsByLicenseKey(licenseKey)) {
                continue;
            }

            Subscription subscription = new Subscription();
            subscription.setTenantId(tenantId);
            subscription.setPlanId(plan.getPublicId());
            subscription.setLicenseKey(licenseKey);
            subscription.setStartsAt(startsAt);
            subscription.setExpiresAt(expiresAt);
            subscription.setMaxStudentsPerSession(plan.getMaxStudentsPerSession());
            subscription.setStatus(SubscriptionStatus.ACTIVE);
            subscription.setActivationSource(source);
            try {
                return subscriptionPersistenceService.save(subscription);
            } catch (DataIntegrityViolationException ex) {
                // The participating transaction is rollback-only after a
                // uniqueness violation. Do not continue inside it: callers
                // retry the complete business transition, including its
                // payment/license linkage and BEFORE_COMMIT intent.
                throw new SubscriptionActivationException(HttpStatus.INTERNAL_SERVER_ERROR,
                        BillingConstants.LICENSE_KEY_GENERATION_FAILED, ex);
            }
        }
        throw new SubscriptionActivationException(HttpStatus.INTERNAL_SERVER_ERROR,
                BillingConstants.LICENSE_KEY_GENERATION_FAILED);
    }

    private Instant calculateExpiryAfterRenewal(ServicePlan plan, Instant startsAt) {
        // Each activation is an independent commercial lane. A still-active
        // subscription must not extend a new purchase's expiry beyond its own
        // start time; overlap is visible, not silently compounded.
        return startsAt.plus(Duration.ofDays(plan.getDurationDays()));
    }

    private void validateRequest(UUID tenantId, ServicePlan plan, ActivationSource source) {
        if (tenantId == null) {
            throw invalid(BillingConstants.SUBSCRIPTION_TENANT_REQUIRED);
        }
        if (plan == null || plan.getPublicId() == null) {
            throw invalid(BillingConstants.SUBSCRIPTION_PLAN_REQUIRED);
        }
        if (source == null) {
            throw invalid(BillingConstants.SUBSCRIPTION_SOURCE_REQUIRED);
        }
        if (plan.isDeleted() || plan.getStatus() != PlanStatus.ACTIVE) {
            throw new SubscriptionActivationException(HttpStatus.CONFLICT,
                    BillingConstants.SUBSCRIPTION_PLAN_NOT_ACTIVE);
        }
        if (plan.getType() == null) {
            throw invalid(BillingConstants.SUBSCRIPTION_PLAN_TYPE_INVALID);
        }
        if (plan.getType() == PlanType.EXAM_PACKAGE
                && (!positive(plan.getDurationDays()) || !positive(plan.getMaxStudentsPerSession())
                        || plan.getExtraStudentSlots() != null)) {
            throw invalid(BillingConstants.SUBSCRIPTION_EXAM_FIELDS_INVALID);
        }
        if (plan.getType() == PlanType.STUDENT_CAPACITY
                && (!positive(plan.getExtraStudentSlots()) || plan.getDurationDays() != null
                        || plan.getMaxStudentsPerSession() != null)) {
            throw invalid(BillingConstants.SUBSCRIPTION_CAPACITY_FIELDS_INVALID);
        }
    }

    private SubscriptionActivationException invalid(String code) {
        return new SubscriptionActivationException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
