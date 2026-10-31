package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.config.ChatAiProperties;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Cached answers now expire after {@code chat.ai.retention-cache-days}, and an expired entry can be replaced. */
@ChatPostgresJpaTest
class ChatAnswerCacheTtlPostgresTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String MODEL = "gemini-2.5-flash-lite";

    @Autowired
    private ChatAnswerCacheRepository repository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private MutableClock clock;
    private ChatAiProperties properties;
    private ChatAnswerCacheService service;
    private ChatTestData data;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        properties = new ChatAiProperties();
        service = new ChatAnswerCacheService(repository, jdbc, clock, properties);
        data = new ChatTestData(jdbc);
    }

    private static String freshKey() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    private Instant expiresAt(String key) {
        return ((Timestamp) data.cacheRow(key).get("expires_at")).toInstant();
    }

    @Test
    void aNewEntryExpiresAfterTheConfiguredNumberOfDays() {
        String key = freshKey();

        service.storeQuietly(key, "answer", MODEL);

        assertThat(expiresAt(key)).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
    }

    @Test
    void theNumberOfDaysComesFromTheProperties() {
        properties.setRetentionCacheDays(7);
        String key = freshKey();

        service.storeQuietly(key, "answer", MODEL);

        assertThat(expiresAt(key)).isEqualTo(NOW.plus(7, ChronoUnit.DAYS));
    }

    @Test
    void anEntryIsServedUntilItExpiresAndNotAfter() {
        String key = freshKey();
        service.storeQuietly(key, "answer", MODEL);

        clock.set(NOW.plus(30, ChronoUnit.DAYS).minusSeconds(1));
        assertThat(service.find(key)).contains("answer");

        clock.set(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(service.find(key)).isEmpty();
    }

    @Test
    void anExpiredEntryIsReplacedByTheNextStoreInsteadOfBlockingIt() {
        String key = freshKey();
        data.insertCache(key, "stale", NOW.minusSeconds(1));

        service.storeQuietly(key, "fresh", MODEL);

        assertThat(service.find(key)).contains("fresh");
        assertThat(expiresAt(key)).isEqualTo(NOW.plus(30, ChronoUnit.DAYS));
        assertThat(data.cacheRows(key)).isEqualTo(1);
    }

    @Test
    void aLiveEntryIsStillFirstWriteWins() {
        String key = freshKey();
        service.storeQuietly(key, "first", MODEL);

        service.storeQuietly(key, "second", MODEL);

        assertThat(service.find(key)).contains("first");
    }

    @Test
    void aPermanentEntryIsNeverReplaced() {
        String key = freshKey();
        data.insertCache(key, "forever", null);

        service.storeQuietly(key, "other", MODEL);

        assertThat(service.find(key)).contains("forever");
    }
}
