package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.support.ChatPostgresJpaTest;
import com.pte.chat.support.FakeGeminiServer;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every failure after the charge, on the real schema: the counter returns to its earlier value, the provider was
 * called exactly once, a FAILED row keeps the cost, and the session still counts as having no answer.
 */
@ChatPostgresJpaTest
class ChatServiceFailurePostgresTest extends ChatFlowPostgresBase {

    private static final int USED_BEFORE = 3;

    private static Arguments scenario(String name, Consumer<FakeGeminiServer> script, long providerTimeoutMs,
                                      String finishReason, int tokensIn, int tokensOut) {
        return Arguments.of(name, script, providerTimeoutMs, finishReason, tokensIn, tokensOut);
    }

    static Stream<Arguments> providerFailures() {
        return Stream.of(
                scenario("http 429", server -> server.enqueue(429, "{\"error\":{\"message\":\"quota\"}}"),
                        5_000, null, 0, 0),
                scenario("http 500", server -> server.enqueue(500, ""), 5_000, null, 0, 0),
                scenario("body is not json", server -> server.enqueue(200, "<<definitely not json>>"),
                        5_000, null, 0, 0),
                scenario("model text is not the schema", server -> server.enqueue(200, GeminiResponses.brokenModelText()),
                        5_000, "STOP", 11, 22),
                scenario("finish reason MAX_TOKENS", server -> server.enqueue(200, GeminiResponses.maxTokens()),
                        5_000, "MAX_TOKENS", 11, 800),
                scenario("timeout", server -> server.enqueueDelayed(3_000, 200, GeminiResponses.onTopic("late")),
                        300, null, 0, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providerFailures")
    void eachProviderFailureRefundsStoresAFailedRowAndCallsTheProviderOnce(
            String name, Consumer<FakeGeminiServer> script, long timeoutMs, String finishReason, int tokensIn,
            int tokensOut) throws Exception {
        ChatFlowHarness h = start(p -> { }, Duration.ofMillis(timeoutMs));
        data.setUsed(alice, ChatFlowHarness.DAY, USED_BEFORE);
        script.accept(h.server);

        assertThatThrownBy(() -> h.send(alice, session, "  A question about the passive voice  "))
                .isInstanceOf(ChatBusyException.class);

        assertThat(h.used(alice)).isEqualTo(USED_BEFORE);
        assertThat(h.server.requestCount()).isEqualTo(1);
        List<Map<String, Object>> rows = data.messages(session);
        assertThat(rows).hasSize(1);
        Map<String, Object> failed = rows.getFirst();
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("answer")).isEqualTo("");
        assertThat(failed.get("question")).isEqualTo("A question about the passive voice");
        assertThat(failed.get("finish_reason")).isEqualTo(finishReason);
        assertThat(failed.get("tokens_in")).isEqualTo(tokensIn);
        assertThat(failed.get("tokens_out")).isEqualTo(tokensOut);
        assertThat(failed.get("on_topic")).isNull();
        assertThat(failed.get("from_cache")).isEqualTo(false);
        assertThat(failed.get("model")).isEqualTo(ChatFlowHarness.MODEL);
        assertThat(h.sessions.hasAnswers(session, alice)).isFalse();
        assertThat(data.cacheRows()).isZero();
    }

    @Test
    void afterAFailureTheStudentIsNotLockedOutAndCanAskAgain() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(500, "");
        h.server.enqueue(200, GeminiResponses.onTopic("second try works"));
        assertThatThrownBy(() -> h.send(alice, session, "question")).isInstanceOf(ChatBusyException.class);

        ChatReply reply = h.send(alice, session, "question");

        assertThat(reply.markdown()).isEqualTo("second try works");
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void aFailureNeverEatsTheLastMessageOfTheDay() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(500, "");
        h.server.enqueue(200, GeminiResponses.onTopic("still got my one message"));
        assertThatThrownBy(() -> h.send(alice, session, "question")).isInstanceOf(ChatBusyException.class);

        assertThat(h.send(alice, session, "question").markdown()).isEqualTo("still got my one message");
    }

    // ---- storing fails -----------------------------------------------------------------------------------

    @Test
    void ifTheGoodAnswerCannotBeStoredTheQuotaIsRefundedAndNothingIsCached() throws IOException {
        ChatFlowHarness h = start();
        data.setUsed(alice, ChatFlowHarness.DAY, USED_BEFORE);
        h.server.enqueue(200, GeminiResponses.onTopic("a good answer that cannot be stored"));
        data.execute("ALTER TABLE chat_message ADD CONSTRAINT boom_no_ok_rows CHECK (status <> 'OK') NOT VALID");
        try {
            assertThatThrownBy(() -> h.send(alice, session, "question")).isInstanceOf(ChatBusyException.class);
        } finally {
            data.execute("ALTER TABLE chat_message DROP CONSTRAINT boom_no_ok_rows");
        }

        assertThat(h.used(alice)).isEqualTo(USED_BEFORE);
        assertThat(h.server.requestCount()).isEqualTo(1);
        assertThat(data.messages(session)).noneMatch(row -> "OK".equals(row.get("status")));
        assertThat(data.cacheRows()).isZero();
        assertThat(h.sessions.hasAnswers(session, alice)).isFalse();
    }

    @Test
    void aCacheWriteThatFailsNeverBreaksTheReply() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("answer despite the broken cache"));
        data.execute("ALTER TABLE chat_answer_cache ADD CONSTRAINT boom_no_inserts CHECK (false) NOT VALID");
        ChatReply reply;
        try {
            reply = h.send(alice, session, "question");
        } finally {
            data.execute("ALTER TABLE chat_answer_cache DROP CONSTRAINT boom_no_inserts");
        }

        assertThat(reply.markdown()).isEqualTo("answer despite the broken cache");
        assertThat(reply.onTopic()).isTrue();
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(data.messages(session)).singleElement()
                .satisfies(row -> assertThat(row.get("status")).isEqualTo("OK"));
        assertThat(data.cacheRows()).isZero();
        assertThat(h.sessions.hasAnswers(session, alice)).isTrue();
    }
}
