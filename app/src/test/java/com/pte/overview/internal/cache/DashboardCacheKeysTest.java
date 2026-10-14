package com.pte.overview.internal.cache;

import com.pte.overview.internal.constant.OverviewConstants;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DashboardCacheKeysTest {

    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final String SERIES = OverviewConstants.SERIES_ATTEMPTS_MONTHLY;

    @Test
    void keyEmbedsPrefixSchemaVersionTenantWindowAndSeries() {
        String key = DashboardCacheKeys.host(TENANT_A, 30, SERIES);

        assertThat(key).isEqualTo("dash:host:v1:" + TENANT_A + ":30:attempts-monthly");
        assertThat(key).contains(OverviewConstants.CACHE_SCHEMA_VERSION).contains(TENANT_A.toString());
    }

    @Test
    void sameInputsAlwaysGiveTheSameKey() {
        assertThat(DashboardCacheKeys.host(TENANT_A, 7, SERIES)).isEqualTo(DashboardCacheKeys.host(TENANT_A, 7, SERIES));
    }

    @Test
    void differentTenantsNeverShareAKey() {
        assertThat(DashboardCacheKeys.host(TENANT_A, 30, SERIES)).isNotEqualTo(DashboardCacheKeys.host(TENANT_B, 30, SERIES));
    }

    @Test
    void differentWindowsNeverShareAKey() {
        assertThat(DashboardCacheKeys.host(TENANT_A, 7, SERIES)).isNotEqualTo(DashboardCacheKeys.host(TENANT_A, 30, SERIES));
        assertThat(DashboardCacheKeys.host(TENANT_A, 30, SERIES)).isNotEqualTo(DashboardCacheKeys.host(TENANT_A, 90, SERIES));
    }

    @Test
    void differentSeriesNeverShareAKey() {
        assertThat(DashboardCacheKeys.host(TENANT_A, 30, "attempts-monthly"))
                .isNotEqualTo(DashboardCacheKeys.host(TENANT_A, 30, "other-series"));
    }

    @Test
    void schemaVersionIsAnIsolatedSegmentSoBumpingItInvalidatesEveryKey() {
        String[] segments = DashboardCacheKeys.host(TENANT_A, 30, SERIES).split(":");

        assertThat(segments).hasSize(6);
        assertThat(segments[2]).isEqualTo(OverviewConstants.CACHE_SCHEMA_VERSION);
        // A different version in that slot produces a different key by construction.
        String bumped = String.join(":", segments[0], segments[1], "v2", segments[3], segments[4], segments[5]);
        assertThat(bumped).isNotEqualTo(DashboardCacheKeys.host(TENANT_A, 30, SERIES));
    }

    @Test
    void everyTenantWindowSeriesCombinationIsUnique() {
        List<UUID> tenants = List.of(TENANT_A, TENANT_B, UUID.randomUUID());
        List<Integer> windows = List.copyOf(OverviewConstants.ALLOWED_WINDOW_DAYS);
        List<String> series = List.of(SERIES, "other");
        Set<String> keys = new HashSet<>();

        for (UUID tenant : tenants) {
            for (int window : windows) {
                for (String s : series) {
                    keys.add(DashboardCacheKeys.host(tenant, window, s));
                }
            }
        }

        assertThat(keys).hasSize(tenants.size() * windows.size() * series.size());
    }

    @Test
    void aHostKeyWithoutATenantIsRejected() {
        assertThatThrownBy(() -> DashboardCacheKeys.host(null, 30, SERIES)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ttlIsThirtySeconds() {
        assertThat(OverviewConstants.HOST_SERIES_TTL.toSeconds()).isEqualTo(30);
    }
}
