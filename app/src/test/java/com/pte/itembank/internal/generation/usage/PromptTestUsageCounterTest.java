package com.pte.itembank.internal.generation.usage;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the counter's script against a real Redis, so atomicity and the expiry are proven, not assumed. */
class PromptTestUsageCounterTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);
    private static final long TWO_DAYS = 2L * 24 * 60 * 60;

    private static GenericContainer<?> redisContainer;
    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redis;
    private static PromptTestUsageCounter counter;

    @BeforeAll
    static void startRedis() {
        redisContainer = new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
        redisContainer.start();
        connectionFactory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();
        counter = new PromptTestUsageCounter(redis);
    }

    @AfterAll
    static void stopRedis() {
        if (connectionFactory != null) {
            connectionFactory.destroy();
        }
        if (redisContainer != null) {
            redisContainer.stop();
        }
    }

    private static String key(UUID user, LocalDate day) {
        return "prompt-test:runs:" + user + ":" + day;
    }

    @Test
    void theFirstRunIsOneAndTheKeyExpiresInAboutTwoDays() {
        UUID user = UUID.randomUUID();

        assertThat(counter.countRun(user, DAY)).isEqualTo(1);

        assertThat(redis.getExpire(key(user, DAY), TimeUnit.SECONDS)).isBetween(TWO_DAYS - 60, TWO_DAYS);
        assertThat(redis.opsForValue().get(key(user, DAY))).isEqualTo("1");
    }

    @Test
    void consecutiveRunsCountUpAndKeepTheirExpiry() {
        UUID user = UUID.randomUUID();

        assertThat(counter.countRun(user, DAY)).isEqualTo(1);
        assertThat(counter.countRun(user, DAY)).isEqualTo(2);
        assertThat(counter.countRun(user, DAY)).isEqualTo(3);

        assertThat(redis.getExpire(key(user, DAY), TimeUnit.SECONDS)).isBetween(TWO_DAYS - 60, TWO_DAYS);
    }

    @Test
    void usersAndDaysAreCountedIndependently() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertThat(counter.countRun(first, DAY)).isEqualTo(1);
        assertThat(counter.countRun(first, DAY)).isEqualTo(2);
        assertThat(counter.countRun(second, DAY)).isEqualTo(1);
        assertThat(counter.countRun(first, DAY.plusDays(1))).isEqualTo(1);
        assertThat(counter.countRun(first, DAY)).isEqualTo(3);
    }

    @Test
    void fiftyConcurrentRunsEachGetTheirOwnNumber() throws Exception {
        UUID user = UUID.randomUUID();
        int runs = 50;
        ExecutorService pool = Executors.newFixedThreadPool(runs);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < runs; i++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return counter.countRun(user, DAY);
                }));
            }
            start.countDown();
            List<Long> numbers = new ArrayList<>();
            for (Future<Long> result : results) {
                numbers.add(result.get(30, TimeUnit.SECONDS));
            }

            assertThat(numbers).doesNotHaveDuplicates()
                    .containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(1, runs).boxed().toList());
            assertThat(numbers.stream().filter(number -> number <= 20)).hasSize(20);
            assertThat(redis.getExpire(key(user, DAY), TimeUnit.SECONDS)).isBetween(TWO_DAYS - 60, TWO_DAYS);
        } finally {
            pool.shutdownNow();
        }
    }
}
