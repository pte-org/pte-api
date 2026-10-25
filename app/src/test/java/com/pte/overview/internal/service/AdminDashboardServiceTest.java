package com.pte.overview.internal.service;

import com.pte.overview.internal.service.admin.AdminDashboardService;
import com.pte.billing.BillingOverviewService.LicenseCounts;
import com.pte.overview.internal.AdminDashboardFixtures;
import com.pte.overview.internal.AdminDashboardFixtures.FakeRedis;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.dto.response.AdminDashboardResponse;
import com.pte.overview.internal.dto.response.DashboardSection;
import com.pte.overview.internal.dto.response.SectionStatus;
import com.pte.overview.internal.exception.InvalidDashboardWindowException;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.PlatformOperation;
import com.pte.shared.security.PlatformOperationPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.pte.overview.internal.AdminDashboardFixtures.CLOCK;
import static com.pte.overview.internal.AdminDashboardFixtures.NOW;
import static com.pte.overview.internal.AdminDashboardFixtures.T1;
import static com.pte.overview.internal.AdminDashboardFixtures.T2;
import static com.pte.overview.internal.AdminDashboardFixtures.T3;
import static com.pte.overview.internal.AdminDashboardFixtures.platform;
import static com.pte.overview.internal.AdminDashboardFixtures.tenantBound;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Gating, caching and isolation of the admin dashboard service over mocked root facades. Permission checks,
 * the visibility-bucket cache key, failure isolation and the shared student-count memo are asserted here;
 * section payload composition is covered by AdminTenantSectionsTest and AdminCommercialSectionsTest.
 */
class AdminDashboardServiceTest {

    /** Phase 4 contract: operations callers see every one of the 10 sections (sorted keys joined with '+'). */
    private static final String ADMIN_BUCKET =
            "applications+learners+licenses+plans+questionBank+recentTenants+revenue+subscriptions+support+tenants";
    /** Phase 4 contract change: academic callers used to land in the 'none' bucket, they now see only the question bank. */
    private static final String ACADEMIC_BUCKET = "questionBank";
    private static final List<String> SECTION_KEYS = List.of("tenants", "learners", "recentTenants", "applications",
            "licenses", "subscriptions", "plans", "support", "revenue", "questionBank");
    private static final List<String> OPERATIONS_KEYS = SECTION_KEYS.subList(0, 9);

    private AdminDashboardFixtures fx;

    @BeforeEach
    void setUp() {
        fx = new AdminDashboardFixtures().stubHappyPath();
    }

    private static List<DashboardSection<?>> sections(AdminDashboardResponse response) {
        return List.of(response.tenants(), response.learners(), response.recentTenants(), response.applications(),
                response.licenses(), response.subscriptions(), response.plans(), response.support(),
                response.revenue(), response.questionBank());
    }

    /** The nine operations sections (everything except the question bank) of a response. */
    private static List<DashboardSection<?>> operationsSections(AdminDashboardResponse response) {
        return sections(response).subList(0, 9);
    }

    private static Map<String, DashboardSection<?>> byKey(AdminDashboardResponse response) {
        List<DashboardSection<?>> all = sections(response);
        Map<String, DashboardSection<?>> map = new java.util.LinkedHashMap<>();
        for (int i = 0; i < SECTION_KEYS.size(); i++) {
            map.put(SECTION_KEYS.get(i), all.get(i));
        }
        return map;
    }

    private void verifyNoFacadeTouched() {
        verifyNoInteractions(fx.tenancy, fx.tenancyOverview, fx.revenueFacade, fx.itembank, fx.identity, fx.attempts, fx.billing, fx.support);
    }

    /** Every facade except the item bank one (the only data an academic caller may read). */
    private void verifyNoOperationsFacadeTouched() {
        verifyNoInteractions(fx.tenancy, fx.tenancyOverview, fx.revenueFacade, fx.identity, fx.attempts, fx.billing, fx.support);
    }

    private void clearFacadeInvocations() {
        clearInvocations(fx.tenancy, fx.tenancyOverview, fx.revenueFacade, fx.itembank, fx.identity, fx.attempts, fx.billing, fx.support);
    }

    // ---- happy path ----

    @Test
    void platformAdminGetsEverySectionOkAndTheWindowEcho() {
        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_ADMIN"), null);

        assertThat(sections(response)).hasSize(10).allSatisfy(section -> {
            assertThat(section.status()).isEqualTo(SectionStatus.OK);
            assertThat(section.data()).isNotNull();
        });
        assertThat(response.meta().windowDays()).isEqualTo(30);
        assertThat(response.meta().windowEnd()).isEqualTo(NOW);
        assertThat(response.meta().windowStart()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(response.meta().generatedAt()).isEqualTo(NOW);
        assertThat(response.meta().zone()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void platformManagerGetsEverySectionOkToo() {
        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_MANAGER"), "30");

        assertThat(sections(response)).hasSize(10)
                .allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
    }

    @ParameterizedTest
    @ValueSource(strings = {"7", "30", "90"})
    void everyAllowedWindowReadsItsOwnRollingRanges(String days) {
        int window = Integer.parseInt(days);
        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_ADMIN"), days);

        assertThat(response.meta().windowDays()).isEqualTo(window);
        verify(fx.tenancyOverview).countTenantsCreatedBetween(NOW.minus(Duration.ofDays(window)), NOW);
        verify(fx.tenancyOverview).countTenantsCreatedBetween(NOW.minus(Duration.ofDays(2L * window)),
                NOW.minus(Duration.ofDays(window)));
        verify(fx.attempts).countDistinctActiveStudentsPlatformWide(NOW.minus(Duration.ofDays(window)), NOW);
        verify(fx.support).countOpenedBetween(NOW.minus(Duration.ofDays(window)), NOW);
        verify(fx.revenueFacade).paidRevenueBetween(NOW.minus(Duration.ofDays(window)), NOW);
        verify(fx.revenueFacade).paidRevenueBetween(NOW.minus(Duration.ofDays(2L * window)),
                NOW.minus(Duration.ofDays(window)));
        verify(fx.itembank).countCreatedBetween(NOW.minus(Duration.ofDays(window)), NOW);
        verify(fx.itembank).countCreatedBetween(NOW.minus(Duration.ofDays(2L * window)),
                NOW.minus(Duration.ofDays(window)));
    }

    @Test
    void blankWindowFallsBackToThirtyDays() {
        assertThat(fx.service(AdminDashboardFixtures.passThroughCache()).build(platform("PLATFORM_ADMIN"), "  ")
                .meta().windowDays()).isEqualTo(30);
    }

    @ParameterizedTest
    @ValueSource(strings = {"15", "abc", "0", "-7", "7.5", "365", "99999999999"})
    void anInvalidWindowIsRejectedBeforeAnyFacadeOrCacheIsTouched(String days) {
        DashboardCache cache = mock(DashboardCache.class);

        assertThatThrownBy(() -> fx.service(cache).build(platform("PLATFORM_ADMIN"), days))
                .isInstanceOf(InvalidDashboardWindowException.class);

        verifyNoFacadeTouched();
        verifyNoInteractions(cache);
    }

    // ---- role matrix: FORBIDDEN sections are never computed nor cached ----

    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_ADMIN", "PLATFORM_MANAGER", "ACADEMIC_MANAGER", "ACADEMIC_STAFF",
            "PLATFORM_AUTHOR"})
    void everySectionStatusFollowsTheRealPolicyForEveryPlatformRole(String role) {
        // Phase 4: revenue needs COMMERCIAL_READ; the question bank needs no operation, only a valid platform caller.
        Map<String, PlatformOperation> required = Map.of(
                "tenants", PlatformOperation.TENANT_READ, "learners", PlatformOperation.TENANT_READ,
                "recentTenants", PlatformOperation.TENANT_READ, "applications", PlatformOperation.APPLICATION_READ,
                "licenses", PlatformOperation.LICENSE_MASKED_READ, "subscriptions", PlatformOperation.COMMERCIAL_READ,
                "plans", PlatformOperation.PLAN_READ_ALL, "support", PlatformOperation.SUPPORT_READ,
                "revenue", PlatformOperation.COMMERCIAL_READ);
        CurrentUser caller = platform(role);
        FakeRedis redis = new FakeRedis();

        AdminDashboardResponse response = fx.service(redis.cache()).build(caller, null);

        byKey(response).forEach((key, section) -> {
            boolean allowed = key.equals("questionBank") ? PlatformOperationPolicy.isPlatformCaller(caller)
                    : PlatformOperationPolicy.can(caller, required.get(key));
            assertThat(section.status()).as("%s for %s", key, role)
                    .isEqualTo(allowed ? SectionStatus.OK : SectionStatus.FORBIDDEN);
            if (allowed) {
                assertThat(section.data()).as("%s data for %s", key, role).isNotNull();
            } else {
                assertThat(section.data()).as("%s data for %s", key, role).isNull();
            }
        });
        long allowedCount = byKey(response).values().stream().filter(s -> s.status() == SectionStatus.OK).count();
        assertThat(redis.store).hasSize((int) allowedCount);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACADEMIC_MANAGER", "ACADEMIC_STAFF", "PLATFORM_AUTHOR"})
    void academicCallersGetEveryOperationsSectionForbiddenWithNullDataAndOnlyTheQuestionBankIsComputedAndCached(
            String role) {
        FakeRedis redis = new FakeRedis();

        AdminDashboardResponse response = fx.service(redis.cache()).build(platform(role), null);

        // Phase 4 contract change: 8 -> 10 sections and the question bank is now visible to academic callers.
        assertThat(sections(response)).hasSize(10);
        assertThat(operationsSections(response)).hasSize(9).allSatisfy(section -> {
            assertThat(section.status()).isEqualTo(SectionStatus.FORBIDDEN);
            assertThat(section.data()).isNull();
        });
        assertThat(response.revenue().status()).isEqualTo(SectionStatus.FORBIDDEN);
        assertThat(response.revenue().data()).isNull();
        assertThat(response.questionBank().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.questionBank().data()).isNotNull();
        // Forbidden sections never touch any operations facade: only the item bank facade was read.
        verifyNoOperationsFacadeTouched();
        verify(fx.itembank).counts();
        // Only the question bank was looked up and cached, and under the academic bucket (never under 'none').
        String key = "dash:admin:v1:" + ACADEMIC_BUCKET + ":30:questionBank";
        assertThat(redis.reads).containsExactly(key);
        assertThat(redis.store.keySet()).containsExactly(key);
    }

    @Test
    void aForbiddenSectionNeverTouchesAMockedCacheOnlyTheQuestionBankDoes() {
        DashboardCache cache = mock(DashboardCache.class);

        fx.service(cache).build(platform("ACADEMIC_STAFF"), "30");

        verify(cache).getOrCompute(eq("dash:admin:v1:" + ACADEMIC_BUCKET + ":30:questionBank"), any(Duration.class),
                any(), any());
        org.mockito.Mockito.verifyNoMoreInteractions(cache);
        verifyNoOperationsFacadeTouched();
    }

    @Test
    void aCallerWithNoVisibleSectionAtAllNeverTouchesAMockedCacheOrAnyFacade() {
        DashboardCache cache = mock(DashboardCache.class);

        fx.service(cache).build(tenantBound("PLATFORM_ADMIN"), "30");
        fx.service(cache).build(tenantBound("HOST_ADMIN"), "30");

        verifyNoInteractions(cache);
        verifyNoFacadeTouched();
    }

    @Test
    void aPlatformRoleCarryingATenantIdIsTreatedAsForbiddenEverywhere() {
        FakeRedis redis = new FakeRedis();

        AdminDashboardResponse response = fx.service(redis.cache()).build(tenantBound("PLATFORM_ADMIN"), null);

        assertThat(sections(response)).allSatisfy(section -> assertThat(section.status())
                .isEqualTo(SectionStatus.FORBIDDEN));
        verifyNoFacadeTouched();
        assertThat(redis.store).isEmpty();
    }

    @Test
    void hostAdminAndStudentCallersAreForbiddenEverywhereToo() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());

        for (CurrentUser caller : List.of(tenantBound("HOST_ADMIN"), tenantBound("STUDENT"), platform("HOST_ADMIN"))) {
            assertThat(sections(service.build(caller, null))).allSatisfy(section -> {
                assertThat(section.status()).isEqualTo(SectionStatus.FORBIDDEN);
                assertThat(section.data()).isNull();
            });
        }
        verifyNoFacadeTouched();
        assertThat(redis.store).isEmpty();
    }

    // ---- cache: keys, TTL, hits ----

    @Test
    void eachAllowedSectionIsCachedUnderTheVisibilityBucketWindowAndSectionForSixtySeconds() {
        FakeRedis redis = new FakeRedis();

        fx.service(redis.cache()).build(platform("PLATFORM_ADMIN"), "30");

        Set<String> expected = SECTION_KEYS.stream().map(key -> "dash:admin:v1:" + ADMIN_BUCKET + ":30:" + key)
                .collect(Collectors.toSet());
        assertThat(redis.store.keySet()).isEqualTo(expected);
        assertThat(redis.ttls.values()).hasSize(10).allMatch(ttl -> ttl.equals(Duration.ofSeconds(60)));
    }

    @Test
    void aSecondRequestWithinTheTtlIsServedFromTheCacheWithoutTouchingAnyFacade() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        AdminDashboardResponse first = service.build(platform("PLATFORM_ADMIN"), "30");
        clearFacadeInvocations();

        AdminDashboardResponse second = service.build(platform("PLATFORM_ADMIN"), "30");

        verifyNoFacadeTouched();
        assertThat(second.tenants()).isEqualTo(first.tenants());
        assertThat(second.learners()).isEqualTo(first.learners());
        assertThat(second.recentTenants()).isEqualTo(first.recentTenants());
        assertThat(second.applications()).isEqualTo(first.applications());
        assertThat(second.licenses()).isEqualTo(first.licenses());
        assertThat(second.subscriptions()).isEqualTo(first.subscriptions());
        assertThat(second.plans()).isEqualTo(first.plans());
        assertThat(second.support()).isEqualTo(first.support());
        assertThat(second.revenue()).isEqualTo(first.revenue());
        assertThat(second.questionBank()).isEqualTo(first.questionBank());
        assertThat(second.revenue().data().currencies()).isNotEmpty();
    }

    @Test
    void aMutationShowsOnlyAfterTheTtlBecauseTheCachedPayloadIsServedUntilThen() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        service.build(platform("PLATFORM_ADMIN"), null);
        when(fx.billing.pendingApplicationCount()).thenReturn(999L);

        AdminDashboardResponse cached = service.build(platform("PLATFORM_ADMIN"), null);
        redis.store.clear(); // the TTL elapsed
        AdminDashboardResponse fresh = service.build(platform("PLATFORM_ADMIN"), null);

        assertThat(cached.applications().data().pending()).isEqualTo(7);
        assertThat(fresh.applications().data().pending()).isEqualTo(999);
    }

    @Test
    void managerAndAdminShareCacheEntriesBecauseTheirVisibilityBucketIsTheSame() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        service.build(platform("PLATFORM_ADMIN"), null);
        clearFacadeInvocations();

        AdminDashboardResponse manager = service.build(platform("PLATFORM_MANAGER"), null);

        assertThat(sections(manager)).allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
        verifyNoFacadeTouched();
        assertThat(redis.store).hasSize(10);
    }

    @Test
    void eachWindowGetsItsOwnEntriesAndNeverReusesAnotherWindowsPayload() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());

        service.build(platform("PLATFORM_ADMIN"), "7");
        service.build(platform("PLATFORM_ADMIN"), "30");
        service.build(platform("PLATFORM_ADMIN"), "90");

        assertThat(redis.store).hasSize(30);
        for (String window : List.of("7", "30", "90")) {
            assertThat(redis.store.keySet().stream().filter(key -> key.contains(":" + window + ":"))).hasSize(10);
        }
    }

    // ---- cache: bucket isolation ----

    @Test
    void anAcademicCallerNeverReceivesAnOperationsPayloadEvenForTheSameWindowAfterAnAdminPopulatedTheCache() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        AdminDashboardResponse admin = service.build(platform("PLATFORM_ADMIN"), "30");
        assertThat(redis.store).hasSize(10);
        clearFacadeInvocations();
        redis.reads.clear();

        AdminDashboardResponse academic = service.build(platform("ACADEMIC_MANAGER"), "30");

        assertThat(operationsSections(academic)).allSatisfy(section -> {
            assertThat(section.status()).isEqualTo(SectionStatus.FORBIDDEN);
            assertThat(section.data()).isNull();
        });
        assertThat(academic.revenue().status()).isEqualTo(SectionStatus.FORBIDDEN);
        assertThat(academic.revenue().data()).isNull();
        assertThat(academic.questionBank().status()).isEqualTo(SectionStatus.OK);
        // The academic bucket is not the operations bucket: the admin's cached question bank is NOT reused (and
        // no admin payload leaks), the academic caller gets its own entry computed once.
        assertThat(redis.reads).as("a forbidden section does not even look into the cache")
                .containsExactly("dash:admin:v1:" + ACADEMIC_BUCKET + ":30:questionBank");
        verifyNoOperationsFacadeTouched();
        verify(fx.itembank, times(1)).counts();
        assertThat(redis.store).hasSize(11);
        assertThat(redis.store.keySet()).noneMatch(key -> key.contains(":none:"));
        assertThat(redis.store.keySet().stream().filter(key -> key.startsWith("dash:admin:v1:" + ACADEMIC_BUCKET + ":")))
                .containsExactly("dash:admin:v1:" + ACADEMIC_BUCKET + ":30:questionBank");
        // The data both callers see for the question bank is identical (same computation, separate cache entries).
        assertThat(academic.questionBank().data()).isEqualTo(admin.questionBank().data());
    }

    @Test
    void evenAPayloadPlantedUnderTheAcademicOrNoneBucketKeyForAnOperationsSectionIsNeverServed() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        service.build(platform("PLATFORM_ADMIN"), "30");
        for (String section : List.of("tenants", "revenue", "subscriptions")) {
            String operationsPayload = redis.store.get("dash:admin:v1:" + ADMIN_BUCKET + ":30:" + section);
            redis.store.put("dash:admin:v1:none:30:" + section, operationsPayload);
            redis.store.put("dash:admin:v1:" + ACADEMIC_BUCKET + ":30:" + section, operationsPayload);
        }
        redis.reads.clear();

        AdminDashboardResponse academic = service.build(platform("ACADEMIC_STAFF"), "30");

        assertThat(academic.tenants().status()).isEqualTo(SectionStatus.FORBIDDEN);
        assertThat(academic.tenants().data()).isNull();
        assertThat(academic.revenue().status()).isEqualTo(SectionStatus.FORBIDDEN);
        assertThat(academic.revenue().data()).isNull();
        assertThat(academic.subscriptions().status()).isEqualTo(SectionStatus.FORBIDDEN);
        assertThat(redis.reads).containsExactly("dash:admin:v1:" + ACADEMIC_BUCKET + ":30:questionBank");
    }

    @Test
    void anAcademicCallerFirstThenAnAdminStillGetsFullDataAndTheCacheHoldsOnlyTheTwoExpectedBuckets() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());

        service.build(platform("PLATFORM_AUTHOR"), "30");
        AdminDashboardResponse admin = service.build(platform("PLATFORM_ADMIN"), "30");

        assertThat(sections(admin)).allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
        assertThat(admin.revenue().data().currencies()).isNotEmpty();
        String operationsPrefix = "dash:admin:v1:" + ADMIN_BUCKET + ":30:";
        String academicPrefix = "dash:admin:v1:" + ACADEMIC_BUCKET + ":30:";
        assertThat(redis.store.keySet()).allMatch(key -> key.startsWith(operationsPrefix) || key.startsWith(academicPrefix));
        // 10 operations-bucket entries + the single academic question bank entry; no academic entry for any other section.
        assertThat(redis.store.keySet().stream().filter(key -> key.startsWith(operationsPrefix))).hasSize(10);
        assertThat(redis.store.keySet().stream().filter(key -> key.startsWith(academicPrefix)))
                .containsExactly(academicPrefix + "questionBank");
    }

    // ---- cache failure and failed sections ----

    @Test
    void whenRedisReadsFailEverySectionIsRecomputedAndStillOk() {
        FakeRedis redis = new FakeRedis();
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down")).when(redis.ops)
                .get(anyString());

        AdminDashboardResponse response = fx.service(redis.cache()).build(platform("PLATFORM_ADMIN"), null);

        assertThat(sections(response)).allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
        verify(fx.tenancyOverview).platformTenantCounts();
        verify(fx.billing).licenseCounts(NOW);
    }

    @Test
    void whenRedisWritesFailTheComputedSectionsAreStillReturned() {
        FakeRedis redis = new FakeRedis();
        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("redis down")).when(redis.ops)
                .set(anyString(), anyString(), any(Duration.class));

        AdminDashboardResponse response = fx.service(redis.cache()).build(platform("PLATFORM_ADMIN"), null);

        assertThat(sections(response)).allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
        assertThat(redis.store).isEmpty();
    }

    @Test
    void whenRedisIsCompletelyDownEveryRequestIsComputedAgain() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("connection refused"));
        AdminDashboardService service = fx.service(new DashboardCache(redis,
                JsonMapper.builder().findAndAddModules().build()));

        service.build(platform("PLATFORM_ADMIN"), null);
        AdminDashboardResponse second = service.build(platform("PLATFORM_ADMIN"), null);

        assertThat(sections(second)).allSatisfy(section -> assertThat(section.status()).isEqualTo(SectionStatus.OK));
        verify(fx.billing, times(2)).planCounts();
    }

    @Test
    void aCorruptCachedPayloadIsIgnoredRecomputedAndOverwritten() {
        FakeRedis redis = new FakeRedis();
        String key = "dash:admin:v1:" + ADMIN_BUCKET + ":30:plans";
        redis.store.put(key, "{not valid json");

        AdminDashboardResponse response = fx.service(redis.cache()).build(platform("PLATFORM_ADMIN"), "30");

        assertThat(response.plans().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.plans().data().active()).isEqualTo(4);
        assertThat(redis.store.get(key)).startsWith("{").contains("\"active\":4");
    }

    @Test
    void aFailedSectionIsNeverCachedAndIsRecomputedOnTheNextRequest() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        when(fx.billing.licenseCounts(NOW)).thenThrow(new IllegalStateException("jdbc:postgresql://secret-host"));

        AdminDashboardResponse failed = service.build(platform("PLATFORM_ADMIN"), "30");

        assertThat(failed.licenses().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(failed.licenses().data()).isNull();
        assertThat(redis.store).hasSize(9);
        assertThat(redis.store.keySet()).noneMatch(key -> key.endsWith(":licenses"));

        doReturn(new LicenseCounts(1, 2, 3, 4, 5)).when(fx.billing).licenseCounts(NOW);
        AdminDashboardResponse recovered = service.build(platform("PLATFORM_ADMIN"), "30");

        assertThat(recovered.licenses().status()).isEqualTo(SectionStatus.OK);
        assertThat(recovered.licenses().data().issuedValid()).isEqualTo(1);
        assertThat(redis.store).hasSize(10);
    }

    @Test
    void aFailedRevenueOrQuestionBankSectionIsNeverCachedAndRecoversOnTheNextRequest() {
        FakeRedis redis = new FakeRedis();
        AdminDashboardService service = fx.service(redis.cache());
        when(fx.revenueFacade.paidRevenueBetween(any(), any())).thenThrow(new IllegalStateException("revenue db down"));
        when(fx.itembank.counts()).thenThrow(new IllegalStateException("bank db down"));

        AdminDashboardResponse failed = service.build(platform("PLATFORM_ADMIN"), "30");

        assertThat(failed.revenue().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(failed.revenue().data()).isNull();
        assertThat(failed.questionBank().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(failed.questionBank().data()).isNull();
        assertThat(failed.tenants().status()).isEqualTo(SectionStatus.OK);
        assertThat(redis.store).hasSize(8);
        assertThat(redis.store.keySet()).noneMatch(key -> key.endsWith(":revenue") || key.endsWith(":questionBank"));

        org.mockito.Mockito.reset(fx.revenueFacade, fx.itembank);
        fx.stubRevenueAndQuestionBank();
        AdminDashboardResponse recovered = service.build(platform("PLATFORM_ADMIN"), "30");

        assertThat(recovered.revenue().status()).isEqualTo(SectionStatus.OK);
        assertThat(recovered.questionBank().status()).isEqualTo(SectionStatus.OK);
        assertThat(redis.store).hasSize(10);
    }

    // ---- per-section failure isolation ----

    private enum Failing {
        TENANTS("tenants"), LEARNERS("learners"), RECENT_TENANTS("recentTenants"), APPLICATIONS("applications"),
        LICENSES("licenses"), SUBSCRIPTIONS("subscriptions"), PLANS("plans"), SUPPORT("support"),
        REVENUE("revenue"), QUESTION_BANK("questionBank");

        final String key;

        Failing(String key) {
            this.key = key;
        }
    }

    private void breakFacadeOf(Failing failing) {
        RuntimeException boom = new IllegalStateException("boom jdbc:postgresql://secret-host");
        switch (failing) {
            case TENANTS -> when(fx.tenancyOverview.platformTenantCounts()).thenThrow(boom);
            case LEARNERS -> when(fx.tenancyOverview.seatLimits()).thenThrow(boom);
            case RECENT_TENANTS -> when(fx.tenancyOverview.recentTenants(5)).thenThrow(boom);
            case APPLICATIONS -> when(fx.billing.pendingApplicationCount()).thenThrow(boom);
            case LICENSES -> when(fx.billing.licenseCounts(NOW)).thenThrow(boom);
            case SUBSCRIPTIONS -> when(fx.billing.subscriptionCounts(NOW)).thenThrow(boom);
            case PLANS -> when(fx.billing.planCounts()).thenThrow(boom);
            case SUPPORT -> when(fx.support.platformTicketCounts()).thenThrow(boom);
            case REVENUE -> when(fx.revenueFacade.paidAmountsPerRange(any())).thenThrow(boom);
            case QUESTION_BANK -> when(fx.itembank.counts()).thenThrow(boom);
            default -> throw new IllegalArgumentException();
        }
    }

    @ParameterizedTest
    @EnumSource(Failing.class)
    void oneFailingFacadeMakesOnlyItsOwnSectionErrorWhileTheRestStayOk(Failing failing) {
        breakFacadeOf(failing);

        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_ADMIN"), null);

        byKey(response).forEach((key, section) -> {
            if (key.equals(failing.key)) {
                assertThat(section.status()).as(key).isEqualTo(SectionStatus.ERROR);
                assertThat(section.data()).as(key).isNull();
            } else {
                assertThat(section.status()).as(key).isEqualTo(SectionStatus.OK);
                assertThat(section.data()).as(key).isNotNull();
            }
        });
    }

    @Test
    void theSharedStudentCountFailingBreaksOnlyLearnersAndRecentTenantsAndIsQueriedOnce() {
        when(fx.identity.studentCountsByTenant()).thenThrow(new IllegalStateException("query timeout"));

        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_ADMIN"), null);

        byKey(response).forEach((key, section) -> {
            boolean dependsOnStudents = key.equals("learners") || key.equals("recentTenants");
            assertThat(section.status()).as(key)
                    .isEqualTo(dependsOnStudents ? SectionStatus.ERROR : SectionStatus.OK);
        });
        verify(fx.identity, times(1)).studentCountsByTenant();
    }

    @Test
    void anUnstubbedFacadeBecomesAnErrorSectionNotAFailedRequest() {
        AdminDashboardFixtures bare = new AdminDashboardFixtures(); // nothing stubbed: facades return null

        AdminDashboardResponse response = bare.service(AdminDashboardFixtures.passThroughCache())
                .build(platform("PLATFORM_ADMIN"), null);

        assertThat(response.tenants().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.tenants().data()).isNull();
        assertThat(response.questionBank().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.questionBank().data()).isNull();
        assertThat(response.meta().windowDays()).isEqualTo(30);
    }

    // ---- the heavy shared query and the per-tenant quota lookup ----

    @Test
    void theGroupedStudentCountRunsExactlyOnceForLearnersAndRecentTenantsTogether() {
        fx.service(AdminDashboardFixtures.passThroughCache()).build(platform("PLATFORM_ADMIN"), null);

        verify(fx.identity, times(1)).studentCountsByTenant();
    }

    @Test
    void theGroupedStudentCountIsAskedAgainOnlyForANewRequest() {
        AdminDashboardService service = fx.service(AdminDashboardFixtures.passThroughCache());

        service.build(platform("PLATFORM_ADMIN"), null);
        service.build(platform("PLATFORM_ADMIN"), null);

        verify(fx.identity, times(2)).studentCountsByTenant();
    }

    @Test
    void thePerTenantQuotaLookupIsNeverCalledFromTheAdminDashboard() {
        fx.service(AdminDashboardFixtures.passThroughCache()).build(platform("PLATFORM_ADMIN"), null);

        verify(fx.tenancy, never()).getStudentQuota(any());
        verify(fx.tenancy, never()).getStudentLimit(any());
        verify(fx.tenancy, never()).countOrganizations(any());
        verify(fx.tenancy, never()).getTenantCode(any());
        verify(fx.tenancy, never()).isActiveTenant(any());
    }

    @Test
    void recentTenantsPlansAndTicketTenantNamesUseOneBatchedCallEachNeverOnePerTenant() {
        fx.service(AdminDashboardFixtures.passThroughCache()).build(platform("PLATFORM_ADMIN"), null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> planIds = ArgumentCaptor.forClass(Collection.class);
        verify(fx.billing, times(1)).currentPlanByTenant(planIds.capture(), eq(NOW));
        assertThat(planIds.getValue()).containsExactly(T1, T2, T3);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> tenantIds = ArgumentCaptor.forClass(Collection.class);
        verify(fx.tenancy, times(1)).findTenantSummaries(tenantIds.capture());
        assertThat(tenantIds.getValue()).containsExactly(T1);
        verify(fx.tenancyOverview, times(1)).recentTenants(5);
        verify(fx.tenancyOverview, times(1)).seatLimits();
        verify(fx.billing, times(1)).oldestPendingApplications(5);
        verify(fx.support, times(1)).oldestOpenTickets(5);
    }

    @Test
    void theNumberOfFacadeCallsDoesNotGrowWithTheNumberOfTenants() {
        List<com.pte.tenancy.RecentTenantView> many = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            many.add(new com.pte.tenancy.RecentTenantView(UUID.randomUUID(), "C" + i, "N" + i, "SCHOOL", "ACTIVE", 10,
                    NOW.minusSeconds(i)));
        }
        when(fx.tenancyOverview.recentTenants(5)).thenReturn(many);
        AtomicInteger planLookups = new AtomicInteger();
        when(fx.billing.currentPlanByTenant(anyCollection(), eq(NOW))).thenAnswer(invocation -> {
            planLookups.incrementAndGet();
            return Map.of();
        });

        fx.service(AdminDashboardFixtures.passThroughCache()).build(platform("PLATFORM_ADMIN"), null);

        assertThat(planLookups).hasValue(1);
        verify(fx.tenancy, never()).getStudentQuota(any());
    }

    @Test
    void theClockIsInjectedSoTheWindowEndsExactlyAtTheFixedInstant() {
        AdminDashboardResponse response = fx.service(AdminDashboardFixtures.passThroughCache(), CLOCK)
                .build(platform("PLATFORM_ADMIN"), null);

        assertThat(response.meta().windowEnd()).isEqualTo(NOW);
    }
}
