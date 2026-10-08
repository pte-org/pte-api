package com.pte.practice.internal.service;

import com.pte.billing.BillingService;
import com.pte.identity.PracticeMembershipStatus;
import com.pte.identity.PracticeIdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.UserStatus;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Server-authoritative practice access policy and reusable authorization seam. */
@Service
public class PracticeEntitlementService {

    private final PracticeIdentityService practiceIdentityService;
    private final BillingService billingService;
    private final TenancyService tenancyService;
    private final boolean strictEntitlementEnabled;
    private final PracticeObservability observability;

    public PracticeEntitlementService(PracticeIdentityService practiceIdentityService,
            BillingService billingService, TenancyService tenancyService,
            @Value("${" + PracticeConstants.PRACTICE_STRICT_ENTITLEMENT_ENABLED_PROPERTY + ":false}")
            boolean strictEntitlementEnabled,
            PracticeObservability observability) {
        this.practiceIdentityService = practiceIdentityService;
        this.billingService = billingService;
        this.tenancyService = tenancyService;
        this.strictEntitlementEnabled = strictEntitlementEnabled;
        this.observability = observability;
    }

    @Transactional
    public PracticeEntitlementResponse getEntitlement(UUID shellUserPublicId, UUID requestedOrganizationId) {
        Optional<PracticeIdentityService.PracticeIdentityView> identity = practiceIdentityService
                .resolveForShellUser(shellUserPublicId);
        if (identity.isEmpty()) {
            return response(shellUserPublicId, UserStatus.SUSPENDED, null, List.of(),
                    PracticeEntitlementState.UNAVAILABLE);
        }

        PracticeIdentityService.PracticeIdentityView view = identity.get();
        if (view.shellUserStatus() != UserStatus.ACTIVE) {
            return response(view.shellUserPublicId(), view.shellUserStatus(), null, List.of(),
                    PracticeEntitlementState.UNAVAILABLE);
        }
        List<UUID> membershipTenantIds = view.memberships().stream()
                .map(PracticeIdentityService.PracticeMembershipView::tenantId)
                .filter(Objects::nonNull)
                .toList();
        var activeTenantIds = tenancyService.findActiveTenantIds(membershipTenantIds);
        List<PracticeIdentityService.PracticeMembershipView> activeMemberships = view.memberships().stream()
                .filter(PracticeIdentityService.PracticeMembershipView::isActiveStudent)
                .filter(membership -> activeTenantIds.contains(membership.tenantId()))
                .sorted(Comparator.comparing(PracticeIdentityService.PracticeMembershipView::tenantId))
                .toList();
        List<PracticeEntitlementResponse.OrganizationContext> organizations = organizationContexts(activeMemberships);

        if (activeMemberships.isEmpty()) {
            boolean hasMembership = view.memberships().stream()
                    .anyMatch(membership -> membership.membershipStatus() == PracticeMembershipStatus.ACTIVE);
            return response(view.shellUserPublicId(), view.shellUserStatus(), null, organizations,
                    hasMembership ? PracticeEntitlementState.UNAVAILABLE : PracticeEntitlementState.LOCKED);
        }

        PracticeIdentityService.PracticeMembershipView selected = selectMembership(
                activeMemberships, requestedOrganizationId);
        if (selected == null) {
            return response(view.shellUserPublicId(), view.shellUserStatus(), null, organizations,
                    PracticeEntitlementState.AMBIGUOUS);
        }

        PracticeEntitlementResponse.OrganizationContext organization = organizationContexts(List.of(selected))
                .stream().findFirst().orElse(null);
        PracticeEntitlementState state = organization != null && isEligible(selected.tenantId())
                ? PracticeEntitlementState.UNLOCKED
                : PracticeEntitlementState.LOCKED;
        return response(view.shellUserPublicId(), view.shellUserStatus(), organization, organizations, state);
    }

    /** Reusable server check for future session/task/media/answer mutations. */
    @Transactional
    public void assertUnlocked(CurrentUser caller, UUID requestedOrganizationId) {
        // Rollback is fail-closed: disabling strict enforcement must not open practice mutations.
        if (!strictEntitlementEnabled || caller == null || caller.userId() == null
                || !caller.hasRole(Role.STUDENT.name())) {
            throw new PracticeNotEntitledException();
        }
        PracticeEntitlementResponse entitlement = getEntitlement(caller.userId(), requestedOrganizationId);
        if (entitlement.practice().state() != PracticeEntitlementState.UNLOCKED) {
            throw new PracticeNotEntitledException();
        }
    }

    private boolean isEligible(UUID tenantId) {
        StudentQuota quota = tenancyService.getStudentQuota(tenantId);
        return quota.current() <= quota.limit() && billingService.hasActiveExamPackageSubscription(tenantId);
    }

    private List<PracticeEntitlementResponse.OrganizationContext> organizationContexts(
            List<PracticeIdentityService.PracticeMembershipView> memberships) {
        List<UUID> tenantIds = memberships.stream().map(PracticeIdentityService.PracticeMembershipView::tenantId)
                .filter(Objects::nonNull).toList();
        Map<UUID, TenantOrganizationSummary> summaries = tenancyService.findTenantOrganizationSummaries(tenantIds);
        List<PracticeEntitlementResponse.OrganizationContext> result = new ArrayList<>();
        for (UUID tenantId : tenantIds) {
            TenantOrganizationSummary summary = summaries.get(tenantId);
            if (summary == null || summary.deleted()) {
                continue;
            }
            result.add(new PracticeEntitlementResponse.OrganizationContext(
                    tenantId, summary.name(), summary.organizationType()));
        }
        return result;
    }

    private PracticeIdentityService.PracticeMembershipView selectMembership(
            List<PracticeIdentityService.PracticeMembershipView> memberships, UUID requestedOrganizationId) {
        if (requestedOrganizationId != null) {
            return memberships.stream()
                    .filter(membership -> requestedOrganizationId.equals(membership.tenantId()))
                    .findFirst().orElse(null);
        }
        return memberships.size() == 1 ? memberships.get(0) : null;
    }

    private PracticeEntitlementResponse response(UUID userPublicId, UserStatus status,
            PracticeEntitlementResponse.OrganizationContext organization,
            List<PracticeEntitlementResponse.OrganizationContext> organizations,
            PracticeEntitlementState state) {
        observability.entitlementEvaluated(state);
        return new PracticeEntitlementResponse(
                new PracticeEntitlementResponse.Student(userPublicId, status == null ? "UNKNOWN" : status.name()),
                organization,
                List.copyOf(organizations),
                new PracticeEntitlementResponse.PracticeStatus(state),
                Instant.now().plus(PracticeConstants.ENTITLEMENT_REFRESH_SECONDS, ChronoUnit.SECONDS));
    }
}
