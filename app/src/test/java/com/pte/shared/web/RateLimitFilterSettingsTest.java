package com.pte.shared.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitFilterSettingsTest {

    private static final String ROUTE = "/api/v1/license-code-redemptions";

    @Test
    void acceptsPositiveLimits() {
        RateLimitSettings settings = new RateLimitSettings(40, 200, 10, Map.of(ROUTE, 5));

        assertThat(settings.routeLimits()).containsEntry(ROUTE, 5);
    }

    @Test
    void rejectsNonPositiveUserLimit() {
        assertThatThrownBy(() -> new RateLimitSettings(0, 200, 10, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTenantLimit() {
        assertThatThrownBy(() -> new RateLimitSettings(40, -1, 10, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveAnonymousLimit() {
        assertThatThrownBy(() -> new RateLimitSettings(40, 200, 0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveRouteLimit() {
        assertThatThrownBy(() -> new RateLimitSettings(40, 200, 10, Map.of(ROUTE, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
