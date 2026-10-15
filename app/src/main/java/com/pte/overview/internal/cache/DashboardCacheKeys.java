package com.pte.overview.internal.cache;

import com.pte.overview.internal.constant.OverviewConstants;

import java.util.UUID;

/**
 * Builds Redis keys. Host keys always embed the tenant id, the window and the
 * schema version, so two tenants (or two windows, or two payload shapes) can
 * never share an entry.
 */
public final class DashboardCacheKeys {

    private static final String SEP = ":";

    private DashboardCacheKeys() {
    }

    public static String host(UUID tenantId, int windowDays, String series) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required for a host cache key");
        }
        return String.join(SEP, OverviewConstants.CACHE_KEY_PREFIX, OverviewConstants.CACHE_SCOPE_HOST,
                OverviewConstants.CACHE_SCHEMA_VERSION, tenantId.toString(), String.valueOf(windowDays), series);
    }

    /**
     * Admin key: the scope is the constant "admin", then schema version, visibility bucket (the
     * computed set of sections the caller may see, never a raw role), window and section. Two
     * callers with different visibility, windows or sections can never share an entry.
     */
    public static String admin(String visibilityBucket, int windowDays, String section) {
        if (visibilityBucket == null || visibilityBucket.isBlank()) {
            throw new IllegalArgumentException("visibilityBucket is required for an admin cache key");
        }
        return String.join(SEP, OverviewConstants.CACHE_KEY_PREFIX, OverviewConstants.CACHE_SCOPE_ADMIN,
                OverviewConstants.CACHE_SCHEMA_VERSION, visibilityBucket, String.valueOf(windowDays), section);
    }
}
