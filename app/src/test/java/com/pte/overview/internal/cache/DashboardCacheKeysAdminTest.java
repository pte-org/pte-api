package com.pte.overview.internal.cache;

import com.pte.overview.internal.constant.OverviewConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Admin cache keys: scope, schema version, visibility bucket, window and section never collide. */
class DashboardCacheKeysAdminTest {

    private static final String ALL = "applications+learners+licenses+plans+recentTenants+subscriptions+support+tenants";
    private static final List<String> BUCKETS = List.of(ALL, "none", "tenants", "learners+tenants");
    private static final List<String> SECTIONS = List.of("tenants", "learners", "recentTenants", "applications",
            "licenses", "subscriptions", "plans", "support");

    @Test
    void keyEmbedsScopeSchemaVersionBucketWindowAndSectionInThatOrder() {
        assertThat(DashboardCacheKeys.admin(ALL, 30, "tenants")).isEqualTo("dash:admin:v1:" + ALL + ":30:tenants");
        assertThat(DashboardCacheKeys.admin("none", 7, "plans")).isEqualTo("dash:admin:v1:none:7:plans");
    }

    @Test
    void sameInputsAlwaysGiveTheSameKey() {
        assertThat(DashboardCacheKeys.admin(ALL, 90, "support")).isEqualTo(DashboardCacheKeys.admin(ALL, 90, "support"));
    }

    @Test
    void theKeyHasExactlySixColonSegmentsBecauseTheBucketUsesPlusNotColon() {
        for (String bucket : BUCKETS) {
            assertThat(DashboardCacheKeys.admin(bucket, 30, "tenants").split(":")).hasSize(6);
        }
        assertThat(OverviewConstants.CACHE_BUCKET_SEPARATOR).doesNotContain(":");
    }

    @Test
    void everyBucketWindowSectionCombinationIsUnique() {
        Set<String> keys = new HashSet<>();
        for (String bucket : BUCKETS) {
            for (int window : OverviewConstants.ALLOWED_WINDOW_DAYS) {
                for (String section : SECTIONS) {
                    keys.add(DashboardCacheKeys.admin(bucket, window, section));
                }
            }
        }

        assertThat(keys).hasSize(BUCKETS.size() * OverviewConstants.ALLOWED_WINDOW_DAYS.size() * SECTIONS.size());
    }

    @Test
    void anOperationsBucketAndTheNoneBucketNeverShareAKeyForTheSameWindowAndSection() {
        for (int window : OverviewConstants.ALLOWED_WINDOW_DAYS) {
            for (String section : SECTIONS) {
                assertThat(DashboardCacheKeys.admin(ALL, window, section))
                        .isNotEqualTo(DashboardCacheKeys.admin("none", window, section));
            }
        }
    }

    @Test
    void theSchemaVersionIsAnIsolatedSegment() {
        String[] segments = DashboardCacheKeys.admin(ALL, 30, "tenants").split(":");

        assertThat(segments[0]).isEqualTo(OverviewConstants.CACHE_KEY_PREFIX);
        assertThat(segments[1]).isEqualTo(OverviewConstants.CACHE_SCOPE_ADMIN);
        assertThat(segments[2]).isEqualTo(OverviewConstants.CACHE_SCHEMA_VERSION);
    }

    @Test
    void anAdminKeyNeverEqualsAHostKey() {
        UUID tenant = UUID.randomUUID();

        assertThat(DashboardCacheKeys.admin("tenants", 30, "attempts-monthly"))
                .isNotEqualTo(DashboardCacheKeys.host(tenant, 30, "attempts-monthly"));
        assertThat(DashboardCacheKeys.admin(ALL, 30, "tenants")).doesNotContain(":host:");
        assertThat(DashboardCacheKeys.host(tenant, 30, "tenants")).doesNotContain(":admin:");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    void aBlankBucketIsRejectedSoNoKeyCanBeSharedByEveryone(String bucket) {
        assertThatThrownBy(() -> DashboardCacheKeys.admin(bucket, 30, "tenants"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theAdminTtlIsSixtySeconds() {
        assertThat(OverviewConstants.ADMIN_SECTION_TTL.toSeconds()).isEqualTo(60);
    }

    @Test
    void theNoneBucketConstantIsTheLiteralNone() {
        assertThat(OverviewConstants.CACHE_BUCKET_NONE).isEqualTo("none");
    }
}
