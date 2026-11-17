package com.pte.shared.web;

import java.util.Map;

/**
 * The request ceilings {@link RateLimitFilter} enforces, all per second: one for each signed-in user, a higher one
 * shared by a whole tenant, and one for each client address that is not signed in. A route listed in
 * {@code routeLimits} (matched on the request URI) uses its own ceiling in place of all three.
 */
public record RateLimitSettings(int userPerSecond, int tenantPerSecond, int anonymousPerSecond,
                                Map<String, Integer> routeLimits) {

    private static final String NOT_POSITIVE = "Rate limits must be positive";

    public RateLimitSettings {
        if (userPerSecond <= 0 || tenantPerSecond <= 0 || anonymousPerSecond <= 0
                || routeLimits.values().stream().anyMatch(limit -> limit == null || limit <= 0)) {
            throw new IllegalArgumentException(NOT_POSITIVE);
        }
        routeLimits = Map.copyOf(routeLimits);
    }
}
