package com.pte.practice.internal.service;

import com.pte.billing.BillingService;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.practice.PracticeEntitlementState;
import com.pte.practice.PracticeObservability;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.dto.response.PracticeEntitlementResponse;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.StudentQuota;
import com.pte.tenancy.TenantOrganizationSummary;
import com.pte.tenancy.TenancyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-authoritative practice access policy and reusable authorization seam.
 *
 * <p>Practice signs in with the roster account an organization issued (plan
 * hung-practice-password-login), so the caller's own tenant is the only
 * organization context: there is no email identity to resolve and no
 * organization to choose.
 */
@Service
public class PracticeEntitlementService {

    private static final String UNKNOWN_STATUS = "UNKNOWN";

    private final IdentityService identityService;
    private final BillingService billingService;
    private final TenancyService tenancyService;
    private final boolean strictEntitlementEnabled;
    private final PracticeObservability observability;

    public PracticeEntitlementService(IdentityService identityService,
            BillingService billingService, TenancyService tenancyService,
            @Value("${" + PracticeConstants.PRACTICE_STRICT_ENTITLEMENT_ENABLED_PROPERTY + ":false}")
            boolean strictEntitlementEnabled,
            PracticeObservability observability) {
        this.identityService = identityService;
        this.billingService = billingService;
        this.tenancyService = tenancyService;
        this.strictEntitlementEnabled = strictEntitlementEnabled;
        this.observability = observability;
    }

    @Transactional(readOnly = true)
    public PracticeEntitlementResponse getEntitlement(CurrentUser caller, UUID requestedOrganizationId) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null
                || !caller.hasRole(Role.STUDENT.name())) {
            return response(caller == null ? null : caller.userId(), UNKNOWN_STATUS, null,
                    PracticeEntitlementState.UNAVAILABLE);
        }
        UUID tenantId = caller.tenantId();
        Optional<User> student = identityService.findActiveStudentInTenant(caller.userId(), tenantId);
        if (student.isEmpty()) {
            return response(caller.userId(), UNKNOWN_STATUS, null, PracticeEntitlementState.UNAVAILABLE);
        }
        String studentStatus = student.get().getStatus().name();
        if (!tenancyService.findActiveTenantIds(List.of(tenantId)).contains(tenantId)) {
            return response(caller.userId(), studentStatus, null, PracticeEntitlementState.UNAVAILABLE);
        }
        TenantOrganizationSummary summary = tenancyService.findTenantOrganizationSummaries(List.of(tenantId))
                .get(tenantId);
        if (summary == null || summary.deleted()) {
            return response(caller.userId(), studentStatus, null, PracticeEntitlementState.UNAVAILABLE);
        }
        PracticeEntitlementResponse.OrganizationContext organization =
                new PracticeEntitlementResponse.OrganizationContext(
                        tenantId, summary.name(), summary.organizationType());
        // A student account belongs to exactly one organization; asking for another one never unlocks it.
        if (requestedOrganizationId != null && !requestedOrganizationId.equals(tenantId)) {
            return response(caller.userId(), studentStatus, organization, PracticeEntitlementState.LOCKED);
        }
        PracticeEntitlementState state = isEligible(tenantId)
                ? PracticeEntitlementState.UNLOCKED
                : PracticeEntitlementState.LOCKED;
        return response(caller.userId(), studentStatus, organization, state);
    }

    /** Reusable server check for session/task/media/answer mutations. */
    @Transactional(readOnly = true)
    public void assertUnlocked(CurrentUser caller, UUID requestedOrganizationId) {
        // Rollback is fail-closed: disabling strict enforcement must not open practice mutations.
        if (!strictEntitlementEnabled || caller == null || caller.userId() == null
                || !caller.hasRole(Role.STUDENT.name())) {
            throw new PracticeNotEntitledException();
        }
        PracticeEntitlementResponse entitlement = getEntitlement(caller, requestedOrganizationId);
        if (entitlement.practice().state() != PracticeEntitlementState.UNLOCKED) {
            throw new PracticeNotEntitledException();
        }
    }

    private boolean isEligible(UUID tenantId) {
        StudentQuota quota = tenancyService.getStudentQuota(tenantId);
        return quota.current() <= quota.limit() && billingService.hasActiveExamPackageSubscription(tenantId);
    }

    private PracticeEntitlementResponse response(UUID userPublicId, String status,
            PracticeEntitlementResponse.OrganizationContext organization, PracticeEntitlementState state) {
        observability.entitlementEvaluated(state);
        return new PracticeEntitlementResponse(
                new PracticeEntitlementResponse.Student(userPublicId, status),
                organization,
                organization == null ? List.of() : List.of(organization),
                new PracticeEntitlementResponse.PracticeStatus(state),
                Instant.now().plus(PracticeConstants.ENTITLEMENT_REFRESH_SECONDS, ChronoUnit.SECONDS));
    }
}
