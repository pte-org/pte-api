package com.pte.overview.internal.cache;

import com.pte.overview.internal.constant.OverviewConstants;
import com.pte.overview.internal.dto.response.MonthlyPoint;
import com.pte.overview.internal.dto.response.MonthlySeries;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardCacheTest {

    private static final Duration TTL = OverviewConstants.HOST_SERIES_TTL;
    private static final UUID TENANT_A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID TENANT_B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();
    private final Map<String, String> store = new HashMap<>();
    private final Map<String, Duration> ttls = new HashMap<>();
    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private DashboardCache cache;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenAnswer(invocation -> store.get((String) invocation.getArgument(0)));
        doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(1));
            ttls.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(ops).set(anyString(), anyString(), any(Duration.class));
        cache = new DashboardCache(redis, jsonMapper);
    }

    private static MonthlySeries series(long marker) {
        return new MonthlySeries(List.of(new MonthlyPoint("2026-09", marker, false),
                new MonthlyPoint("2026-10", marker + 1, true)));
    }

    private static String key(UUID tenant, int days) {
        return DashboardCacheKeys.host(tenant, days, OverviewConstants.SERIES_ATTEMPTS_MONTHLY);
    }

    @Test
    void missComputesWritesWithTheTtlAndReturnsTheValue() {
        String key = key(TENANT_A, 30);

        MonthlySeries value = cache.getOrCompute(key, TTL, MonthlySeries.class, () -> series(10));

        assertThat(value).isEqualTo(series(10));
        assertThat(store).containsOnlyKeys(key);
        assertThat(ttls.get(key)).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void hitReturnsTheCachedValueWithoutRunningTheLoader() {
        String key = key(TENANT_A, 30);
        cache.getOrCompute(key, TTL, MonthlySeries.class, () -> series(10));
        AtomicInteger loads = new AtomicInteger();

        MonthlySeries second = cache.getOrCompute(key, TTL, MonthlySeries.class, () -> {
            loads.incrementAndGet();
            return series(99);
        });

        assertThat(second).isEqualTo(series(10));
        assertThat(loads).hasValue(0);
    }

    @Test
    void tenantsNeverSeeEachOthersCachedSeries() {
        MonthlySeries a = cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> series(100));
        MonthlySeries b = cache.getOrCompute(key(TENANT_B, 30), TTL, MonthlySeries.class, () -> series(200));
        AtomicInteger loads = new AtomicInteger();

        MonthlySeries aAgain = cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> {
            loads.incrementAndGet();
            return series(-1);
        });
        MonthlySeries bAgain = cache.getOrCompute(key(TENANT_B, 30), TTL, MonthlySeries.class, () -> {
            loads.incrementAndGet();
            return series(-1);
        });

        assertThat(a).isEqualTo(series(100));
        assertThat(b).isEqualTo(series(200));
        assertThat(aAgain).isEqualTo(series(100));
        assertThat(bAgain).isEqualTo(series(200));
        assertThat(loads).hasValue(0);
        assertThat(store).hasSize(2);
        assertThat(store.keySet()).anyMatch(k -> k.contains(TENANT_A.toString()))
                .anyMatch(k -> k.contains(TENANT_B.toString()));
    }

    @Test
    void windowsAreCachedSeparatelyForTheSameTenant() {
        cache.getOrCompute(key(TENANT_A, 7), TTL, MonthlySeries.class, () -> series(7));
        MonthlySeries ninety = cache.getOrCompute(key(TENANT_A, 90), TTL, MonthlySeries.class, () -> series(90));

        assertThat(ninety).isEqualTo(series(90));
        assertThat(store).hasSize(2);
    }

    @Test
    void roundTripPreservesEveryFieldOfTheSeries() {
        String key = key(TENANT_A, 30);
        MonthlySeries original = series(5);
        cache.getOrCompute(key, TTL, MonthlySeries.class, () -> original);

        MonthlySeries restored = cache.getOrCompute(key, TTL, MonthlySeries.class, () -> series(-1));

        assertThat(restored.points()).containsExactlyElementsOf(original.points());
        assertThat(restored.points().get(1).partial()).isTrue();
    }

    @Test
    void redisReadFailureFallsBackToComputingDirectly() {
        when(ops.get(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        MonthlySeries value = cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> series(1));

        assertThat(value).isEqualTo(series(1));
    }

    @Test
    void redisWriteFailureStillReturnsTheComputedValue() {
        doThrow(new RedisConnectionFailureException("redis down"))
                .when(ops).set(anyString(), anyString(), any(Duration.class));

        MonthlySeries value = cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> series(2));

        assertThat(value).isEqualTo(series(2));
    }

    @Test
    void redisCompletelyDownStillServesEveryRequest() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("connection refused"));
        AtomicInteger loads = new AtomicInteger();

        for (int i = 0; i < 3; i++) {
            MonthlySeries value = cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> {
                loads.incrementAndGet();
                return series(3);
            });
            assertThat(value).isEqualTo(series(3));
        }

        assertThat(loads).hasValue(3);
    }

    @Test
    void corruptCachedPayloadIsIgnoredAndOverwritten() {
        String key = key(TENANT_A, 30);
        store.put(key, "{not valid json");

        MonthlySeries value = cache.getOrCompute(key, TTL, MonthlySeries.class, () -> series(4));

        assertThat(value).isEqualTo(series(4));
        assertThat(jsonMapper.readValue(store.get(key), MonthlySeries.class)).isEqualTo(series(4));
    }

    @Test
    void loaderFailureIsNotSwallowedAndNothingIsCached() {
        String key = key(TENANT_A, 30);

        assertThatThrownBy(() -> cache.getOrCompute(key, TTL, MonthlySeries.class, () -> {
            throw new IllegalStateException("db down");
        })).isInstanceOf(IllegalStateException.class).hasMessage("db down");

        verify(ops, never()).set(eq(key), anyString(), any(Duration.class));
        assertThat(store).isEmpty();
    }

    @Test
    void loaderFailureIsNotSwallowedEvenWhenRedisIsAlsoDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("connection refused"));

        assertThatThrownBy(() -> cache.getOrCompute(key(TENANT_A, 30), TTL, MonthlySeries.class, () -> {
            throw new IllegalStateException("db down too");
        })).isInstanceOf(IllegalStateException.class).hasMessage("db down too");
    }
}
