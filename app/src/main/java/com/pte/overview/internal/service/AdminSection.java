package com.pte.overview.internal.service;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;

import java.util.function.Predicate;

/**
 * The section-to-permission table of the admin dashboard. Each section is checked
 * independently against the existing platform operation policy; legacy roles are already
 * canonicalized by {@code CurrentUser}. Later phases add rows (never rewrite them).
 */
public enum AdminSection {
    TENANTS(OverviewConstants.SECTION_TENANTS, requires(PlatformOperation.TENANT_READ)),
    LEARNERS(OverviewConstants.SECTION_LEARNERS, requires(PlatformOperation.TENANT_READ)),
    RECENT_TENANTS(OverviewConstants.SECTION_RECENT_TENANTS, requires(PlatformOperation.TENANT_READ)),
    APPLICATIONS(OverviewConstants.SECTION_APPLICATIONS, requires(PlatformOperation.APPLICATION_READ)),
    LICENSES(OverviewConstants.SECTION_LICENSES, requires(PlatformOperation.LICENSE_MASKED_READ)),
    SUBSCRIPTIONS(OverviewConstants.SECTION_SUBSCRIPTIONS, requires(PlatformOperation.COMMERCIAL_READ)),
    PLANS(OverviewConstants.SECTION_PLANS, requires(PlatformOperation.PLAN_READ_ALL)),
    SUPPORT(OverviewConstants.SECTION_SUPPORT, requires(PlatformOperation.SUPPORT_READ)),
    REVENUE(OverviewConstants.SECTION_REVENUE, requires(PlatformOperation.COMMERCIAL_READ)),
    /** Visible to any valid platform caller; the controller already limits who reaches the dashboard. */
    QUESTION_BANK(OverviewConstants.SECTION_QUESTION_BANK, PlatformOperationPolicy::isPlatformCaller);

    private final String key;
    private final Predicate<CurrentUser> gate;

    AdminSection(String key, Predicate<CurrentUser> gate) {
        this.key = key;
        this.gate = gate;
    }

    public String key() {
        return key;
    }

    public boolean isAllowedFor(CurrentUser caller) {
        return gate.test(caller);
    }

    private static Predicate<CurrentUser> requires(PlatformOperation operation) {
        return caller -> PlatformOperationPolicy.can(caller, operation);
    }
}
