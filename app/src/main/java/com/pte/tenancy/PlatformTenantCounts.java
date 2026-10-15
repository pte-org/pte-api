package com.pte.tenancy;

import java.util.Map;

/**
 * Platform-wide tenant counts for dashboards. {@code byOrganizationType} is keyed by the
 * stored organization type; deleted tenants are never counted.
 */
public record PlatformTenantCounts(long active, long suspended, Map<String, Long> byOrganizationType) {

    public long total() {
        return active + suspended;
    }
}
