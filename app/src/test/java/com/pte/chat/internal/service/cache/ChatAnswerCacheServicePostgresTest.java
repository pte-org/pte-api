package com.pte.chat.internal.service.cache;

import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.repository.ChatAnswerCacheRepository;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.ChatTestData;
import com.pte.chat.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/** The shared answer cache on the real schema: find honours expiry, store is first-write-wins and never throws. */
@ChatPostgresJpaTest
class ChatAnswerCacheServicePostgresTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final String MODEL = "gemini-2.5-flash-lite";

    @Autowired
    private ChatAnswerCacheRepository repository;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    private MutableClock clock;
    private ChatAnswerCacheService service;
    private ChatTestData data;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(NOW);
        service = new ChatAnswerCacheService(repository, jdbc, clock);
        data = new ChatTestData(jdbc);
    }

    private static String freshKey() {
        // 64 lowercase hex characters, like ChatCacheKey produces
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    @Test
    void anUnknownKeyIsAMiss() {
        assertThat(service.find(freshKey())).isEmpty();
    }

    @Test
    void aStoredAnswerIsFoundByItsKey() {
        String key = freshKey();

        service.storeQuietly(key, "**Use** the present perfect.", MODEL);

        assertThat(service.find(key)).contains("**Use** the present perfect.");
    }

    @Test
    void theStoredRowCarriesTheCurrentPromptVersionAndModel() {
        String key = freshKey();

        service.storeQuietly(key, "answer", MODEL);

        Map<String, Object> row = data.cacheRow(key);
        assertThat(row.get("prompt_version")).isEqualTo(ChatPrompts.VERSION);
        assertThat(row.get("model")).isEqualTo(MODEL);
        assertThat(row.get("answer")).isEqualTo("answer");
    }

    @Test
    void storingTheSameKeyAgainKeepsTheFirstAnswerAndDoesNotFail() {
        String key = freshKey();
        service.storeQuietly(key, "first", MODEL);

        assertThatCode(() -> service.storeQuietly(key, "second", "other-model")).doesNotThrowAnyException();

        assertThat(service.find(key)).contains("first");
        assertThat(data.cacheRows(key)).isEqualTo(1);
    }

    @Test
    void anExpiredEntryIsHidden() {
        String key = freshKey();
        data.insertCache(key, "stale", NOW.minusSeconds(1));

        assertThat(service.find(key)).isEmpty();
    }

    @Test
    void anEntryExpiringExactlyNowIsAlreadyHidden() {
        String key = freshKey();
        data.insertCache(key, "stale", NOW);

        assertThat(service.find(key)).isEmpty();
    }

    @Test
    void anEntryExpiringInTheFutureIsVisibleUntilTheClockPassesIt() {
        String key = freshKey();
        data.insertCache(key, "fresh", NOW.plusSeconds(60));

        assertThat(service.find(key)).contains("fresh");

        clock.set(NOW.plusSeconds(60));

        assertThat(service.find(key)).isEmpty();
    }

    @Test
    void anEntryWithoutAnExpiryNeverExpires() {
        String key = freshKey();
        data.insertCache(key, "forever", null);

        clock.set(Instant.parse("2036-01-01T00:00:00Z"));

        assertThat(service.find(key)).contains("forever");
    }

    @Test
    void anAnswerTooLongForTheColumnIsSwallowedAndNothingIsStored() {
        String key = freshKey();

        assertThatCode(() -> service.storeQuietly(key, "x".repeat(20_001), MODEL)).doesNotThrowAnyException();

        assertThat(data.cacheRows(key)).isZero();
        assertThat(service.find(key)).isEmpty();
    }

    @Test
    void aKeyTooLongForTheColumnIsSwallowed() {
        assertThatCode(() -> service.storeQuietly("k".repeat(65), "answer", MODEL)).doesNotThrowAnyException();
    }

    @Test
    void aModelTooLongForTheColumnIsSwallowed() {
        String key = freshKey();

        assertThatCode(() -> service.storeQuietly(key, "answer", "m".repeat(65))).doesNotThrowAnyException();

        assertThat(data.cacheRows(key)).isZero();
    }

    @Test
    void aNullAnswerIsSwallowed() {
        String key = freshKey();

        assertThatCode(() -> service.storeQuietly(key, null, MODEL)).doesNotThrowAnyException();

        assertThat(data.cacheRows(key)).isZero();
    }

    @Test
    void aFailureOfOneStoreDoesNotPoisonTheNext() {
        service.storeQuietly(freshKey(), "x".repeat(20_001), MODEL);
        String key = freshKey();

        service.storeQuietly(key, "fine", MODEL);

        assertThat(service.find(key)).contains("fine");
    }
}
