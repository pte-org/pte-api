package com.pte.chat.internal.service;

import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The daily cap on free cache hits on a real database. The harness clock is 2026-10-28 15:00 in Vietnam, so the
 * Vietnam day began at 2026-10-27T17:00:00Z.
 */
@ChatPostgresJpaTest
class ChatServiceCacheCapPostgresTest extends ChatFlowPostgresBase {

    private static final String QUESTION = "How do I use the present perfect?";
    private static final String ANSWER = "Use **has/have** + V3.";
    private static final Instant DAY_START = Instant.parse("2026-10-27T17:00:00Z");

    private void insertCacheServed(UUID student, Instant createdAt, boolean deleted) {
        jdbc.update("INSERT INTO chat_message (public_id, created_at, updated_at, deleted, session_id, student_id, "
                        + "tenant_id, question, answer, status, on_topic, from_cache) "
                        + "VALUES (:pid, :c, :c, :del, :sid, :stu, :tenant, 'q', 'a', 'OK', true, true)",
                new MapSqlParameterSource("pid", UUID.randomUUID()).addValue("c", Timestamp.from(createdAt))
                        .addValue("del", deleted).addValue("sid", UUID.randomUUID()).addValue("stu", student)
                        .addValue("tenant", UUID.randomUUID()));
    }

    private void seedCache(ChatFlowHarness h) {
        h.cache.storeQuietly(ChatCacheKey.of(QUESTION, "en", null, ChatPrompts.VERSION), ANSWER, "m");
    }

    @Test
    void afterTheCapIsReachedTheNextReplayInANewSessionIsChargedAndCallsTheProvider() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(2));
        seedCache(h);

        assertThat(h.send(alice, UUID.randomUUID(), QUESTION).fromCache()).isTrue();
        assertThat(h.send(alice, UUID.randomUUID(), QUESTION).fromCache()).isTrue();
        assertThat(h.used(alice)).isZero();
        assertThat(h.server.requestCount()).isZero();

        h.server.enqueue(200, GeminiResponses.onTopic("fresh provider answer"));
        UUID third = UUID.randomUUID();
        ChatReply reply = h.send(alice, third, QUESTION);

        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo("fresh provider answer");
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);
        Map<String, Object> row = data.messages(third).getFirst();
        assertThat(row.get("from_cache")).isEqualTo(false);
        assertThat(row.get("status")).isEqualTo("OK");
    }

    @Test
    void anotherStudentStillGetsAFreeHitWhenTheFirstOneIsPastTheCap() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(2));
        seedCache(h);
        h.send(alice, UUID.randomUUID(), QUESTION);
        h.send(alice, UUID.randomUUID(), QUESTION);

        ChatReply reply = h.send(bob, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.used(bob)).isZero();
        assertThat(h.server.requestCount()).isZero();
    }

    @Test
    void aCapOfOneAllowsExactlyOneFreeHit() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(1));
        seedCache(h);

        assertThat(h.send(alice, UUID.randomUUID(), QUESTION).fromCache()).isTrue();

        h.server.enqueue(200, GeminiResponses.onTopic("charged now"));
        assertThat(h.send(alice, UUID.randomUUID(), QUESTION).fromCache()).isFalse();
        assertThat(h.used(alice)).isEqualTo(1);
    }

    @Test
    void cacheServedRowsFromYesterdayInVietnamAreNotCounted() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(2));
        seedCache(h);
        insertCacheServed(alice, DAY_START.minusSeconds(1), false);
        insertCacheServed(alice, DAY_START.minusSeconds(3600), false);
        insertCacheServed(alice, DAY_START.minusSeconds(86_400), false);

        ChatReply reply = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.used(alice)).isZero();
        assertThat(h.server.requestCount()).isZero();
    }

    @Test
    void aRowAtExactlyMidnightInVietnamIsCounted() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(1));
        seedCache(h);
        insertCacheServed(alice, DAY_START, false);
        h.server.enqueue(200, GeminiResponses.onTopic("charged"));

        ChatReply reply = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isFalse();
        assertThat(h.used(alice)).isEqualTo(1);
    }

    @Test
    void aSoftDeletedCacheServedRowIsNotCounted() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(1));
        seedCache(h);
        insertCacheServed(alice, DAY_START.plusSeconds(60), true);

        ChatReply reply = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.server.requestCount()).isZero();
    }

    @Test
    void aProviderServedAnswerTodayIsNotACacheHit() throws IOException {
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(1));
        seedCache(h);
        h.server.enqueue(200, GeminiResponses.onTopic("provider answer"));
        h.send(alice, session, "a different opening question");

        ChatReply reply = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
    }

    @Test
    void theCapResetsWhenTheVietnamDayRollsOver() throws IOException {
        // Stored rows are stamped by the entity with the real time, so yesterday is seeded by hand.
        ChatFlowHarness h = start(p -> p.setMaxCacheHitsPerDay(1));
        seedCache(h);
        insertCacheServed(alice, DAY_START.plusSeconds(3600), false);
        h.server.enqueue(200, GeminiResponses.onTopic("charged"));
        assertThat(h.send(alice, UUID.randomUUID(), QUESTION).fromCache()).isFalse();

        h.clock.set(Instant.parse("2026-10-28T17:00:01Z"));
        ChatReply nextDay = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(nextDay.fromCache()).isTrue();
        assertThat(h.server.requestCount()).isEqualTo(1);
    }
}
