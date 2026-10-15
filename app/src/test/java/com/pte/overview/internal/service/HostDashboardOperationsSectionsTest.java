package com.pte.overview.internal.service;

import com.pte.billing.BillingOverviewService;
import com.pte.overview.internal.HostDashboardFixtures;
import com.pte.overview.internal.cache.DashboardCache;
import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.HostDashboardResponse;
import com.pte.overview.internal.dto.response.HostSectionData.ActivityItem;
import com.pte.overview.internal.dto.response.MonthlySeries;
import com.pte.overview.internal.dto.response.SectionStatus;
import com.pte.scoring.ScoringOverviewService.ScoringBacklog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Supplier;

import static com.pte.overview.internal.HostDashboardFixtures.NOW;
import static com.pte.overview.internal.HostDashboardFixtures.OTHER_TENANT;
import static com.pte.overview.internal.HostDashboardFixtures.TENANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Phase 2 sections of the host dashboard: wiring, per-section isolation, what is cached (only the grading backlog),
 * cross-tenant cache isolation and the guarantee that no payment link or provider data can appear in the response.
 */
class HostDashboardOperationsSectionsTest {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();

    private HostDashboardFixtures fx;
    private HostDashboardService service;

    @BeforeEach
    void setUp() {
        fx = new HostDashboardFixtures().stubFullPath(TENANT);
        service = fx.service(HostDashboardFixtures.passThroughCache());
    }

    private enum Ops { GRADING_SCORING, GRADING_REPORTING, SUBSCRIPTION, BILLING, SUPPORT, RECENT_ACTIVITY }

    private static SectionStatus statusOf(HostDashboardResponse r, String section) {
        return switch (section) {
            case "learners" -> r.learners().status();
            case "quota" -> r.quota().status();
            case "academics" -> r.academics().status();
            case "staff" -> r.staff().status();
            case "exams" -> r.exams().status();
            case "attempts" -> r.attempts().status();
            case "grading" -> r.grading().status();
            case "subscription" -> r.subscription().status();
            case "billing" -> r.billing().status();
            case "support" -> r.support().status();
            case "recentActivity" -> r.recentActivity().status();
            default -> throw new IllegalArgumentException(section);
        };
    }

    private static final List<String> ALL_SECTIONS = List.of("learners", "quota", "academics", "staff", "exams",
            "attempts", "grading", "subscription", "billing", "support", "recentActivity");

    // ---- happy path ----

    @Test
    void allElevenSectionsAreOkAndCarryTheFacadeNumbers() {
        HostDashboardResponse response = service.build(TENANT, null);

        for (String section : ALL_SECTIONS) {
            assertThat(statusOf(response, section)).as(section).isEqualTo(SectionStatus.OK);
        }
        var grading = response.grading().data();
        assertThat(grading.pendingAnswers()).isEqualTo(5);
        assertThat(grading.aiScoringAnswers()).isEqualTo(2);
        assertThat(grading.failedAnswers()).isEqualTo(1);
        assertThat(grading.closedSessionsAwaitingFinalisation()).isEqualTo(3);
        assertThat(grading.reportsAwaitingPublication()).isEqualTo(4);

        var subscription = response.subscription().data();
        assertThat(subscription.current()).hasSize(1);
        assertThat(subscription.current().get(0).planName()).isEqualTo("Tenant A Exam Pack");
        assertThat(subscription.nearestExpiryAt()).isEqualTo(HostDashboardFixtures.NEAREST_EXPIRY);
        assertThat(subscription.daysToExpiry()).isEqualTo(12L);
        assertThat(subscription.expiringSoon()).isTrue();

        assertThat(response.billing().data().pendingOrders()).isEqualTo(2);
        var support = response.support().data();
        assertThat(support.open()).isEqualTo(3);
        assertThat(support.inProgress()).isEqualTo(2);
        assertThat(support.resolvedAwaitingClose()).isEqualTo(1);

        List<ActivityItem> activity = response.recentActivity().data();
        assertThat(activity).hasSize(2);
        assertThat(activity.get(0).id()).isEqualTo(HostDashboardFixtures.AUDIT_ID);
        assertThat(activity.get(0).action()).isEqualTo("CLASS_CREATED");
        assertThat(activity.get(0).actorUserId()).isEqualTo(HostDashboardFixtures.ACTOR_ID);
        assertThat(activity.get(0).targetType()).isEqualTo("CLASS");
        assertThat(activity.get(0).occurredAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void recentActivityAsksForExactlyTheEightNewestEntriesOfTheCallersTenant() {
        service.build(TENANT, null);

        verify(fx.audit).recent(TENANT, 8);
        verify(fx.audit, never()).recent(eq(OTHER_TENANT), org.mockito.ArgumentMatchers.anyInt());
        assertThat(OverviewConstants.RECENT_ACTIVITY_LIMIT).isEqualTo(8);
    }

    @Test
    void everyPhaseTwoFacadeIsCalledOnlyWithTheCallersTenant() {
        fx.stubOperationsOfTenantB(OTHER_TENANT);

        service.build(TENANT, null);

        verify(fx.scoring).backlog(TENANT);
        verify(fx.reporting).countAwaitingPublication(TENANT);
        verify(fx.billing).subscriptionSummary(TENANT);
        verify(fx.billing).pendingOrderCount(TENANT);
        verify(fx.support).ticketCounts(TENANT);
        verify(fx.scoring, never()).backlog(OTHER_TENANT);
        verify(fx.reporting, never()).countAwaitingPublication(OTHER_TENANT);
        verify(fx.billing, never()).subscriptionSummary(OTHER_TENANT);
        verify(fx.billing, never()).pendingOrderCount(OTHER_TENANT);
        verify(fx.support, never()).ticketCounts(OTHER_TENANT);
    }

    // ---- per-section isolation ----

    @ParameterizedTest
    @EnumSource(Ops.class)
    void aFailingPhaseTwoSectionIsErrorWhileEveryOtherSectionStaysOk(Ops failing) {
        RuntimeException failure = new IllegalStateException("simulated outage: secret-detail");
        String failingSection = switch (failing) {
            case GRADING_SCORING -> {
                when(fx.scoring.backlog(TENANT)).thenThrow(failure);
                yield "grading";
            }
            case GRADING_REPORTING -> {
                when(fx.reporting.countAwaitingPublication(TENANT)).thenThrow(failure);
                yield "grading";
            }
            case SUBSCRIPTION -> {
                when(fx.billing.subscriptionSummary(TENANT)).thenThrow(failure);
                yield "subscription";
            }
            case BILLING -> {
                when(fx.billing.pendingOrderCount(TENANT)).thenThrow(failure);
                yield "billing";
            }
            case SUPPORT -> {
                when(fx.support.ticketCounts(TENANT)).thenThrow(failure);
                yield "support";
            }
            case RECENT_ACTIVITY -> {
                when(fx.audit.recent(TENANT, 8)).thenThrow(failure);
                yield "recentActivity";
            }
        };

        HostDashboardResponse response = service.build(TENANT, null);

        for (String section : ALL_SECTIONS) {
            assertThat(statusOf(response, section)).as("%s when %s fails", section, failing)
                    .isEqualTo(section.equals(failingSection) ? SectionStatus.ERROR : SectionStatus.OK);
        }
        assertThat(response.toString()).doesNotContain("secret-detail");
    }

    @Test
    void aFailingScoringSectionLeavesGradingEmptyButEveryoneElseUntouched() {
        when(fx.scoring.backlog(TENANT)).thenThrow(new RuntimeException("scoring down"));

        HostDashboardResponse response = service.build(TENANT, null);

        assertThat(response.grading().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.grading().data()).isNull();
        assertThat(response.subscription().data().daysToExpiry()).isEqualTo(12L);
        assertThat(response.billing().data().pendingOrders()).isEqualTo(2);
        assertThat(response.support().data().open()).isEqualTo(3);
        assertThat(response.recentActivity().data()).hasSize(2);
        assertThat(response.attempts().data().monthlySubmitted()).hasSize(12);
        assertThat(response.learners().data().total()).isEqualTo(100);
    }

    @Test
    void unstubbedPhaseTwoFacadesDegradeToErrorSectionsNotToAFailedRequest() {
        // A facade that yields nothing (mock default null) must be isolated, never thrown to the caller.
        HostDashboardFixtures core = new HostDashboardFixtures().stubHappyPath(TENANT);

        HostDashboardResponse response = core.service(HostDashboardFixtures.passThroughCache()).build(TENANT, null);

        assertThat(response.learners().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.grading().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.subscription().status()).isEqualTo(SectionStatus.ERROR);
        assertThat(response.support().status()).isEqualTo(SectionStatus.ERROR);
    }

    @Test
    void aTenantWithNoSubscriptionYieldsAnOkSectionWithNullExpiry() {
        when(fx.billing.subscriptionSummary(TENANT)).thenReturn(
                new BillingOverviewService.SubscriptionSummary(List.of(), null, null, false));

        var subscription = service.build(TENANT, null).subscription();

        assertThat(subscription.status()).isEqualTo(SectionStatus.OK);
        assertThat(subscription.data().current()).isEmpty();
        assertThat(subscription.data().nearestExpiryAt()).isNull();
        assertThat(subscription.data().daysToExpiry()).isNull();
        assertThat(subscription.data().expiringSoon()).isFalse();
    }

    // ---- no payment link / provider data anywhere ----

    @Test
    void responseJsonHasNoPaymentLinkUrlOrProviderFieldInBillingOrSubscription() throws Exception {
        JsonNode root = JSON.readTree(JSON.writeValueAsString(service.build(TENANT, null)));

        List<String> keys = new ArrayList<>();
        collectKeys(root.get("billing"), keys);
        collectKeys(root.get("subscription"), keys);
        assertThat(keys).isNotEmpty();
        for (String key : keys) {
            String lower = key.toLowerCase(Locale.ROOT);
            assertThat(lower).as("key " + key).doesNotContain("payment").doesNotContain("link")
                    .doesNotContain("url").doesNotContain("provider").doesNotContain("checkout")
                    .doesNotContain("licen").doesNotContain("amount");
        }
        // The only order datum is a count.
        assertThat(fieldNames(root.get("billing").get("data"))).containsExactly("pendingOrders");
        assertThat(root.get("billing").get("data").get("pendingOrders").isIntegralNumber()).isTrue();
    }

    @Test
    void subscriptionEntriesExposeExactlyTheDocumentedFields() throws Exception {
        JsonNode root = JSON.readTree(JSON.writeValueAsString(service.build(TENANT, null)));

        JsonNode entry = root.get("subscription").get("data").get("current").get(0);

        assertThat(fieldNames(entry)).containsExactlyInAnyOrder("publicId", "planName", "planType", "startsAt",
                "expiresAt", "status");
        assertThat(fieldNames(root.get("subscription").get("data")))
                .containsExactlyInAnyOrder("current", "nearestExpiryAt", "daysToExpiry", "expiringSoon");
    }

    @Test
    void noStringValueAnywhereInTheResponseLooksLikeAUrl() throws Exception {
        String json = JSON.writeValueAsString(service.build(TENANT, null));

        assertThat(json).doesNotContain("http://").doesNotContain("https://").doesNotContain("paymentLink");
    }

    @Test
    void facadeAndSectionReturnTypesCannotCarryPaymentData() {
        List<Class<?>> types = List.of(BillingOverviewService.SubscriptionSummary.class,
                com.pte.billing.CurrentSubscriptionView.class,
                com.pte.overview.internal.dto.response.HostSectionData.Billing.class,
                com.pte.overview.internal.dto.response.HostSectionData.Subscription.class);
        for (Class<?> type : types) {
            for (RecordComponent component : type.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertThat(name).as(type.getSimpleName() + "." + name).doesNotContain("payment")
                        .doesNotContain("link").doesNotContain("url").doesNotContain("provider")
                        .doesNotContain("licen").doesNotContain("amount");
            }
        }
    }

    @Test
    void recentActivityNeverExposesSummaryTextTargetIdOrIp() throws Exception {
        JsonNode root = JSON.readTree(JSON.writeValueAsString(service.build(TENANT, null)));

        JsonNode item = root.get("recentActivity").get("data").get(0);

        assertThat(fieldNames(item)).containsExactlyInAnyOrder("id", "action", "actorUserId", "targetType",
                "occurredAt");
        assertThat(root.toString()).doesNotContain("SECRET summary text").doesNotContain("agg-1");
    }

    // ---- caching ----

    private static final class CacheCall {
        final String key;
        final Duration ttl;
        final Class<?> type;

        CacheCall(String key, Duration ttl, Class<?> type) {
            this.key = key;
            this.ttl = ttl;
            this.type = type;
        }
    }

    @SuppressWarnings("unchecked")
    private static DashboardCache recordingPassThroughCache(List<CacheCall> calls) {
        DashboardCache cache = mock(DashboardCache.class);
        when(cache.getOrCompute(anyString(), any(), any(), any())).thenAnswer(invocation -> {
            calls.add(new CacheCall(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2)));
            return ((Supplier<Object>) invocation.getArgument(3)).get();
        });
        return cache;
    }

    @Test
    void onlyTheGradingBacklogAndTheMonthlySeriesGoThroughTheCache() {
        List<CacheCall> calls = new ArrayList<>();

        recordingService(calls).build(TENANT, null);

        assertThat(calls).hasSize(2);
        assertThat(calls).extracting(c -> c.type.getSimpleName())
                .containsExactlyInAnyOrder(MonthlySeries.class.getSimpleName(), ScoringBacklog.class.getSimpleName());
        CacheCall grading = calls.stream().filter(c -> c.type == ScoringBacklog.class).findFirst().orElseThrow();
        assertThat(grading.key).isEqualTo("dash:host:v1:" + TENANT + ":30:grading");
        assertThat(grading.ttl).isEqualTo(Duration.ofSeconds(30)).isEqualTo(OverviewConstants.HOST_GRADING_TTL);
    }

    private HostDashboardService recordingService(List<CacheCall> calls) {
        return fx.service(recordingPassThroughCache(calls));
    }

    @Test
    void reportsSubscriptionBillingSupportAndActivityAreReadOnEveryRequestEvenWithACache() {
        List<CacheCall> calls = new ArrayList<>();
        HostDashboardService cached = recordingService(calls);

        cached.build(TENANT, null);
        cached.build(TENANT, null);

        verify(fx.reporting, times(2)).countAwaitingPublication(TENANT);
        verify(fx.billing, times(2)).subscriptionSummary(TENANT);
        verify(fx.billing, times(2)).pendingOrderCount(TENANT);
        verify(fx.support, times(2)).ticketCounts(TENANT);
        verify(fx.audit, times(2)).recent(TENANT, 8);
        // None of those ever appears as a cache lookup: only the two cacheable payloads do.
        assertThat(calls).extracting(c -> c.type.getSimpleName())
                .containsOnly(MonthlySeries.class.getSimpleName(), ScoringBacklog.class.getSimpleName());
        assertThat(calls).extracting(c -> c.key).allMatch(k -> k.endsWith(":grading") || k.endsWith(":attempts-monthly"));
    }

    private final Map<String, String> redisStore = new HashMap<>();

    @SuppressWarnings("unchecked")
    private DashboardCache realCacheOverFakeRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(invocation -> redisStore.get((String) invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisStore.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        return new DashboardCache(redis, JSON);
    }

    @Test
    void aCachedGradingBacklogIsServedFromTheCacheWhileTheReportsCountStaysLive() {
        HostDashboardService cached = fx.service(realCacheOverFakeRedis());

        var first = cached.build(TENANT, null).grading().data();
        when(fx.scoring.backlog(TENANT)).thenReturn(new ScoringBacklog(99, 98, 97, 96));
        when(fx.reporting.countAwaitingPublication(TENANT)).thenReturn(40L);
        var second = cached.build(TENANT, null).grading().data();

        assertThat(first.pendingAnswers()).isEqualTo(5);
        // backlog fields are the cached snapshot (the ScoringBacklog record round-trips through the real JSON cache)
        assertThat(second.pendingAnswers()).isEqualTo(5);
        assertThat(second.aiScoringAnswers()).isEqualTo(2);
        assertThat(second.failedAnswers()).isEqualTo(1);
        assertThat(second.closedSessionsAwaitingFinalisation()).isEqualTo(3);
        // reports awaiting publication is NOT cached
        assertThat(second.reportsAwaitingPublication()).isEqualTo(40);
        verify(fx.scoring, times(1)).backlog(TENANT);
        assertThat(redisStore.keySet()).contains("dash:host:v1:" + TENANT + ":30:grading");
    }

    @Test
    void uncachedSectionsReflectChangesImmediatelyWhileGradingIsCached() {
        HostDashboardService cached = fx.service(realCacheOverFakeRedis());
        cached.build(TENANT, null);
        when(fx.billing.pendingOrderCount(TENANT)).thenReturn(9L);
        when(fx.support.ticketCounts(TENANT)).thenReturn(new com.pte.support.SupportOverviewService.TicketCounts(8, 7, 6));

        HostDashboardResponse second = cached.build(TENANT, null);

        assertThat(second.billing().data().pendingOrders()).isEqualTo(9);
        assertThat(second.support().data().open()).isEqualTo(8);
        assertThat(second.support().data().resolvedAwaitingClose()).isEqualTo(6);
    }

    @Test
    void gradingCacheKeysDifferPerTenantAndWindow() {
        fx.stubFullPath(OTHER_TENANT);
        List<CacheCall> calls = new ArrayList<>();
        HostDashboardService cached = recordingService(calls);

        cached.build(TENANT, "30");
        cached.build(TENANT, "7");
        cached.build(OTHER_TENANT, "30");

        List<String> gradingKeys = calls.stream().filter(c -> c.type == ScoringBacklog.class).map(c -> c.key).toList();
        assertThat(gradingKeys).hasSize(3).doesNotHaveDuplicates();
        assertThat(gradingKeys).anyMatch(k -> k.contains(TENANT.toString()) && k.contains(":7:"));
        assertThat(gradingKeys).anyMatch(k -> k.contains(OTHER_TENANT.toString()));
    }

    @Test
    void aCachedBacklogOfTenantANeverLeaksIntoTenantBsDashboard() {
        fx.stubOperationsOfTenantB(OTHER_TENANT).stubHappyPath(OTHER_TENANT);
        HostDashboardService cached = fx.service(realCacheOverFakeRedis());

        HostDashboardResponse a = cached.build(TENANT, null);
        HostDashboardResponse b = cached.build(OTHER_TENANT, null);
        HostDashboardResponse aAgain = cached.build(TENANT, null);

        assertThat(a.grading().data().pendingAnswers()).isEqualTo(5);
        assertThat(b.grading().data().pendingAnswers()).isEqualTo(70707);
        assertThat(b.grading().data().closedSessionsAwaitingFinalisation()).isEqualTo(60606);
        assertThat(aAgain.grading().data().pendingAnswers()).isEqualTo(5);
        assertThat(b.subscription().data().current().get(0).planName()).isEqualTo("TENANT-B-SECRET-PLAN");
        assertThat(a.toString()).doesNotContain("TENANT-B-SECRET");
        assertThat(aAgain.toString()).doesNotContain("TENANT-B-SECRET").doesNotContain("70707");
        assertThat(new TreeSet<>(redisStore.keySet())).anyMatch(k -> k.contains(TENANT.toString()))
                .anyMatch(k -> k.contains(OTHER_TENANT.toString()));
    }

    @Test
    void aBrokenCacheStillYieldsAnOkGradingSection() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        HostDashboardService broken = fx.service(new DashboardCache(redis, JSON));

        HostDashboardResponse response = broken.build(TENANT, null);

        assertThat(response.grading().status()).isEqualTo(SectionStatus.OK);
        assertThat(response.grading().data().pendingAnswers()).isEqualTo(5);
        verify(fx.scoring, times(1)).backlog(TENANT);
    }

    // ---- helpers ----

    private static void collectKeys(JsonNode node, List<String> keys) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            for (String name : node.propertyNames()) {
                keys.add(name);
                collectKeys(node.get(name), keys);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collectKeys(child, keys));
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
