package com.pte.overview.internal.cache;

import com.pte.overview.internal.AdminDashboardFixtures.FakeRedis;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.AdminSectionData.Applications;
import com.pte.overview.internal.dto.response.AdminSectionData.Learners;
import com.pte.overview.internal.dto.response.AdminSectionData.Licenses;
import com.pte.overview.internal.dto.response.AdminSectionData.OldestTicket;
import com.pte.overview.internal.dto.response.AdminSectionData.PendingApplication;
import com.pte.overview.internal.dto.response.AdminSectionData.Plans;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenant;
import com.pte.overview.internal.dto.response.AdminSectionData.RecentTenants;
import com.pte.overview.internal.dto.response.AdminSectionData.SeatUtilisation;
import com.pte.overview.internal.dto.response.AdminSectionData.Subscriptions;
import com.pte.overview.internal.dto.response.AdminSectionData.Support;
import com.pte.overview.internal.dto.response.AdminSectionData.Tenants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.overview.internal.dto.response.TrendValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The admin sections are cached as JSON. Every cached payload type (they contain Instant, Map, List, nested
 * records, nullable fields and Double values) must survive serialize -> deserialize unchanged, both through the
 * mapper Spring Boot auto-configures for the application and through a bare mapper, and through a real
 * {@link DashboardCache} over an in-memory Redis.
 */
class AdminSectionCacheRoundTripTest {

    private static final UUID A = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID B = UUID.fromString("b2000000-0000-0000-0000-000000000002");
    private static final Instant MICROS = Instant.parse("2026-10-10T01:02:03.123456Z");
    private static final Instant NANOS = Instant.parse("2026-10-10T01:02:03.123456789Z");
    private static final Instant WHOLE = Instant.parse("2026-10-10T01:02:03Z");

    /** The mapper the running application uses: Spring Boot's auto-configured JsonMapper. */
    private static JsonMapper productionMapper() {
        AtomicReference<JsonMapper> mapper = new AtomicReference<>();
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .run(context -> mapper.set(context.getBean(JsonMapper.class)));
        return mapper.get();
    }

    private static JsonMapper bareMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    record Sample(String name, Class<?> type, Object value) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static List<MonthlyPoint> months() {
        List<Long> counts = LongStream.rangeClosed(1, 12).boxed().toList();
        return java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> new MonthlyPoint(String.format("2025-%02d", i + 1), counts.get(i), i == 11)).toList();
    }

    private static Map<String, Long> twoEntries() {
        Map<String, Long> map = new LinkedHashMap<>();
        map.put("SCHOOL", 12L);
        map.put("CENTER", 8L);
        return map;
    }

    private static List<Sample> samples() {
        return List.of(
                new Sample("tenants (full)", Tenants.class, new Tenants(20, 18, 2, twoEntries(),
                        new TrendValue(6, 4, 50.0), months())),
                new Sample("tenants (null change, empty map and list)", Tenants.class,
                        new Tenants(0, 0, 0, Map.of(), new TrendValue(0, 0, null), List.of())),
                new Sample("learners (full)", Learners.class, new Learners(156, 760, new TrendValue(300, 250, 20.0),
                        List.of(new SeatUtilisation(A, "T4", "Delta Academy", 10, 10, 100.0),
                                new SeatUtilisation(B, "T1", "Alpha School", 33, 100, 33.3)))),
                new Sample("learners (empty ranking, negative trend)", Learners.class,
                        new Learners(0, 0, new TrendValue(1, 9, -88.9), List.of())),
                new Sample("recentTenants (instants incl. micro and nano precision, nulls)", RecentTenants.class,
                        new RecentTenants(List.of(
                                new RecentTenant(A, "T1", "Alpha", "SCHOOL", "ACTIVE", "Pro Pack", 90, 100, MICROS, NANOS),
                                new RecentTenant(B, "T2", "Beta", "CENTER", "SUSPENDED", null, 0, 50, null, WHOLE)))),
                new Sample("recentTenants (empty)", RecentTenants.class, new RecentTenants(List.of())),
                new Sample("applications (full)", Applications.class, new Applications(7, new TrendValue(5, 10, -50.0),
                        List.of(new PendingApplication(A, "Org One", "SCHOOL", "ORG1", MICROS),
                                new PendingApplication(B, "Org Two", "CENTER", "ORG2", NANOS)))),
                new Sample("applications (empty)", Applications.class,
                        new Applications(0, new TrendValue(0, 0, null), List.of())),
                new Sample("licenses", Licenses.class, new Licenses(40, 15, 3, 9, 6, 7, new TrendValue(8, 8, 0.0))),
                new Sample("subscriptions", Subscriptions.class, new Subscriptions(33, 5, 30,
                        new TrendValue(7, 2, 250.0), twoEntries())),
                new Sample("plans", Plans.class, new Plans(4, 2, 1, twoEntries())),
                new Sample("support (full)", Support.class, new Support(11, 4, new TrendValue(9, 3, 200.0),
                        List.of(new OldestTicket(A, B, "Alpha", "BUG", MICROS),
                                new OldestTicket(B, A, null, "CONTENT_COMPLAINT", NANOS)))),
                new Sample("support (empty)", Support.class, new Support(0, 0, new TrendValue(0, 0, null), List.of())));
    }

    private static final JsonMapper PRODUCTION = productionMapper();
    private static final JsonMapper BARE = bareMapper();

    static Stream<Arguments> everySampleOnBothMappers() {
        return samples().stream().flatMap(sample -> Stream.of(
                Arguments.of("production (Spring Boot)", PRODUCTION, sample),
                Arguments.of("bare findAndAddModules", BARE, sample)));
    }

    @Test
    void everyCachedAdminSectionTypeHasASample() {
        assertThat(samples().stream().map(Sample::type).distinct().toList()).containsExactlyInAnyOrder(Tenants.class,
                Learners.class, RecentTenants.class, Applications.class, Licenses.class, Subscriptions.class,
                Plans.class, Support.class);
    }

    @Test
    void theProductionMapperIsAvailableForTheRoundTrip() {
        assertThat(productionMapper()).isNotNull();
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("everySampleOnBothMappers")
    void serializeThenDeserializeGivesAnEqualRecord(String label, JsonMapper mapper, Sample sample) {
        String json = mapper.writeValueAsString(sample.value());

        Object restored = mapper.readValue(json, sample.type());

        assertThat(restored).isEqualTo(sample.value());
    }

    @ParameterizedTest(name = "{0}: {2}")
    @MethodSource("everySampleOnBothMappers")
    void aRealCacheRoundTripReturnsTheCachedValueWithoutRunningTheLoaderAgain(String label, JsonMapper mapper,
            Sample sample) {
        FakeRedis redis = new FakeRedis(mapper);
        DashboardCache cache = redis.cache();
        String key = DashboardCacheKeys.admin("tenants", 30, "section");
        AtomicInteger loads = new AtomicInteger();

        Object first = read(cache, key, sample.type(), () -> {
            loads.incrementAndGet();
            return sample.value();
        });
        Object second = read(cache, key, sample.type(), () -> {
            loads.incrementAndGet();
            return null;
        });

        assertThat(first).isEqualTo(sample.value());
        assertThat(second).as("the value read back from the cache").isEqualTo(sample.value());
        assertThat(loads).hasValue(1);
        assertThat(redis.store).containsOnlyKeys(key);
        assertThat(redis.ttls.get(key)).isEqualTo(OverviewConstants.ADMIN_SECTION_TTL);
    }

    private static <T> T read(DashboardCache cache, String key, Class<T> type, Supplier<Object> loader) {
        return cache.getOrCompute(key, OverviewConstants.ADMIN_SECTION_TTL, type, () -> type.cast(loader.get()));
    }

    @Test
    void instantsKeepTheirFullPrecisionThroughTheProductionMapper() {
        JsonMapper mapper = productionMapper();
        RecentTenants original = new RecentTenants(List.of(
                new RecentTenant(A, "T1", "Alpha", "SCHOOL", "ACTIVE", null, 1, 1, null, NANOS)));

        RecentTenants restored = mapper.readValue(mapper.writeValueAsString(original), RecentTenants.class);

        assertThat(restored.items().get(0).createdAt()).isEqualTo(NANOS);
    }

    @Test
    void theCachedJsonOfEverySectionCarriesNoLicenseCodeOrContactKeys() {
        JsonMapper mapper = productionMapper();
        for (Sample sample : samples()) {
            String json = mapper.writeValueAsString(sample.value()).toLowerCase();

            assertThat(json).as(sample.name()).doesNotContain("licensecode").doesNotContain("licensekey")
                    .doesNotContain("email").doesNotContain("taxcode").doesNotContain("description")
                    .doesNotContain("password").doesNotContain("secret").doesNotContain("token");
        }
    }
}
