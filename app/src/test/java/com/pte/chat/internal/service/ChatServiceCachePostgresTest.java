package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.internal.exception.ChatSessionNotFoundException;
import com.pte.chat.internal.prompt.ChatPrompts;
import com.pte.chat.internal.service.cache.ChatCacheKey;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The answer cache across real requests: a repeated first question is free and never reaches the provider, and
 * the cache is consulted and written only for a session that has no answer yet.
 */
@ChatPostgresJpaTest
class ChatServiceCachePostgresTest extends ChatFlowPostgresBase {

    private static final String QUESTION = "How do I use the present perfect?";
    private static final String ANSWER = "Use **has/have** + V3.";

    private static String key(String question, String language, String taskType) {
        return ChatCacheKey.of(question, language, taskType, ChatPrompts.VERSION);
    }

    private ChatReply send(ChatFlowHarness h, UUID student, UUID sessionId, String question, String taskType,
                           String language) {
        return h.service.send(ChatFlowHarness.student(student), new ChatSendCommand(sessionId, question, taskType, language));
    }

    // ---- hits ----------------------------------------------------------------------------------------------

    @Test
    void anAnsweredFirstQuestionIsCachedAfterItsMessageIsStored() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));

        h.send(alice, session, QUESTION);

        assertThat(data.cacheRows()).isEqualTo(1);
        Map<String, Object> row = data.cacheRow(key(QUESTION, "en", null));
        assertThat(row.get("answer")).isEqualTo(ANSWER);
        assertThat(row.get("model")).isEqualTo(GeminiResponses.SERVED_MODEL);
        assertThat(row.get("prompt_version")).isEqualTo(ChatPrompts.VERSION);
    }

    @Test
    void theSameFirstQuestionInANewSessionIsServedFromTheCacheForFree() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.send(alice, session, QUESTION);
        UUID second = UUID.randomUUID();

        ChatReply reply = h.send(alice, second, "  HOW do I   use the PRESENT perfect?\n");

        assertThat(reply.markdown()).isEqualTo(ANSWER);
        assertThat(reply.fromCache()).isTrue();
        assertThat(reply.onTopic()).isTrue();
        assertThat(reply.sessionId()).isEqualTo(second);
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(reply.quota().used()).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(1);
        Map<String, Object> row = data.messages(second).getFirst();
        assertThat(row.get("status")).isEqualTo("OK");
        assertThat(row.get("from_cache")).isEqualTo(true);
        assertThat(row.get("tokens_in")).isEqualTo(0);
        assertThat(row.get("tokens_out")).isEqualTo(0);
        assertThat(row.get("answer")).isEqualTo(ANSWER);
        assertThat(row.get("student_id")).isEqualTo(alice);
        assertThat(data.sessionOwner(second)).contains(alice);
        assertThat(h.sessions.hasAnswers(second, alice)).isTrue();
    }

    @Test
    void anotherStudentGetsTheCachedAnswerWithoutSpendingAnyOfTheirQuota() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.send(alice, session, QUESTION);

        ChatReply reply = h.send(bob, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.used(bob)).isZero();
        assertThat(data.usageRows(bob)).isZero();
        assertThat(reply.quota().used()).isZero();
        assertThat(reply.quota().remaining()).isEqualTo(20);
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    @Test
    void aCacheHitIsStillServedWhenTheDailyQuotaIsExhausted() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.send(alice, session, QUESTION);

        ChatReply reply = h.send(alice, UUID.randomUUID(), QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.used(alice)).isEqualTo(1);
    }

    @Test
    void aCacheHitInAnotherStudentsSessionIsStillNotFound() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.send(alice, session, QUESTION);

        assertThatThrownBy(() -> h.send(bob, session, QUESTION)).isInstanceOf(ChatSessionNotFoundException.class);

        assertThat(data.messageRowsForStudent(bob)).isZero();
        assertThat(data.sessionOwner(session)).contains(alice);
    }

    // ---- misses ---------------------------------------------------------------------------------------------

    @Test
    void aDifferentLanguageIsACacheMiss() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.server.enqueue(200, GeminiResponses.onTopic("Dung thi hien tai hoan thanh."));
        send(h, alice, session, QUESTION, null, "en");

        ChatReply reply = send(h, alice, UUID.randomUUID(), QUESTION, null, "vi");

        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo("Dung thi hien tai hoan thanh.");
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void aDifferentTaskTypeIsACacheMiss() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));
        h.server.enqueue(200, GeminiResponses.onTopic("task specific"));
        send(h, alice, session, QUESTION, "WRITE_ESSAY", "en");

        ChatReply reply = send(h, alice, UUID.randomUUID(), QUESTION, "READ_ALOUD", "en");

        assertThat(reply.fromCache()).isFalse();
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void anAnswerCachedUnderAnOlderPromptVersionIsNotServed() throws IOException {
        ChatFlowHarness h = start();
        h.cache.storeQuietly(ChatCacheKey.of(QUESTION, "en", null, "v0-older-prompt"), "stale answer", "old-model");
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));

        ChatReply reply = h.send(alice, session, QUESTION);

        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo(ANSWER);
    }

    @Test
    void anExpiredCacheEntryIsNotServed() throws IOException {
        ChatFlowHarness h = start();
        data.insertCache(key(QUESTION, "en", null), "expired answer", Instant.parse("2026-10-28T07:59:59Z"));
        h.server.enqueue(200, GeminiResponses.onTopic(ANSWER));

        ChatReply reply = h.send(alice, session, QUESTION);

        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo(ANSWER);
    }

    // ---- a session that already has an answer -------------------------------------------------------------

    @Test
    void theSameQuestionInASessionThatAlreadyHasAnAnswerGoesToTheProviderAndIgnoresTheCache() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("first answer"));
        h.send(alice, session, "an opening question");
        h.cache.storeQuietly(key(QUESTION, "en", null), "stale cached answer", "m");
        h.server.enqueue(200, GeminiResponses.onTopic("fresh answer from the provider"));

        ChatReply reply = h.send(alice, session, QUESTION);

        assertThat(reply.fromCache()).isFalse();
        assertThat(reply.markdown()).isEqualTo("fresh answer from the provider");
        assertThat(h.used(alice)).isEqualTo(2);
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void aFollowUpAnswerIsNeverWrittenToTheCache() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("opening answer"));
        h.send(alice, session, "an opening question");
        h.server.enqueue(200, GeminiResponses.onTopic("follow up answer"));

        h.send(alice, session, "a follow up that only makes sense in context");

        assertThat(data.cacheRows()).isEqualTo(1);
        assertThat(data.cacheRows(key("a follow up that only makes sense in context", "en", null))).isZero();
    }

    @Test
    void aRefusedMessageAloneDoesNotMakeTheSessionNonEmptySoTheCacheStillApplies() throws IOException {
        ChatFlowHarness h = start();
        h.cache.storeQuietly(key(QUESTION, "en", null), ANSWER, "m");
        h.server.enqueue(200, GeminiResponses.offTopic());
        h.send(alice, session, "what is the weather");

        ChatReply reply = h.send(alice, session, QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(reply.markdown()).isEqualTo(ANSWER);
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    @Test
    void aFailedMessageAloneDoesNotMakeTheSessionNonEmptySoTheCacheStillApplies() throws IOException {
        ChatFlowHarness h = start();
        h.cache.storeQuietly(key(QUESTION, "en", null), ANSWER, "m");
        h.server.enqueue(500, "");
        assertThatThrownBy(() -> h.send(alice, session, "this one fails")).isInstanceOf(ChatBusyException.class);

        ChatReply reply = h.send(alice, session, QUESTION);

        assertThat(reply.fromCache()).isTrue();
        assertThat(h.server.requestCount()).isEqualTo(1);
    }

    // ---- what is cached ---------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"offTopic", "providerFailure"})
    void neitherARefusalNorAFailureIsEverCached(String outcome) throws IOException {
        ChatFlowHarness h = start();
        if ("offTopic".equals(outcome)) {
            h.server.enqueue(200, GeminiResponses.offTopic());
            h.send(alice, session, QUESTION);
        } else {
            h.server.enqueue(500, "");
            assertThatThrownBy(() -> h.send(alice, session, QUESTION)).isInstanceOf(ChatBusyException.class);
        }

        assertThat(data.cacheRows()).isZero();
    }
}
