package com.pte.chat.internal.service;

import com.pte.chat.internal.exception.ChatBusyException;
import com.pte.chat.support.FakeGeminiServer;
import com.pte.chat.support.GeminiResponses;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every failure after the charge, on the real schema: the counter returns to its earlier value, the provider was
 * called exactly once and the day's counters keep the tokens the failed call cost.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChatServiceFailurePostgresTest extends ChatFlowPostgresBase {

    private static final int USED_BEFORE = 3;

    private static Arguments scenario(String name, Consumer<FakeGeminiServer> script, long providerTimeoutMs,
                                      int tokensIn, int tokensOut) {
        return Arguments.of(name, script, providerTimeoutMs, tokensIn, tokensOut);
    }

    static Stream<Arguments> providerFailures() {
        return Stream.of(
                scenario("http 429", server -> server.enqueue(429, "{\"error\":{\"message\":\"quota\"}}"),
                        5_000, 0, 0),
                scenario("http 500", server -> server.enqueue(500, ""), 5_000, 0, 0),
                scenario("body is not json", server -> server.enqueue(200, "<<definitely not json>>"),
                        5_000, 0, 0),
                scenario("model text is not the schema", server -> server.enqueue(200, GeminiResponses.brokenModelText()),
                        5_000, 11, 22),
                scenario("finish reason MAX_TOKENS", server -> server.enqueue(200, GeminiResponses.maxTokens()),
                        5_000, 11, 800),
                scenario("timeout", server -> server.enqueueDelayed(3_000, 200, GeminiResponses.onTopic("late")),
                        300, 0, 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("providerFailures")
    void eachProviderFailureRefundsCountsAFailedMessageAndCallsTheProviderOnce(
            String name, Consumer<FakeGeminiServer> script, long timeoutMs, int tokensIn, int tokensOut)
            throws Exception {
        ChatFlowHarness h = start(p -> { }, Duration.ofMillis(timeoutMs));
        data.setUsed(alice, ChatFlowHarness.DAY, USED_BEFORE);
        script.accept(h.server);

        assertThatThrownBy(() -> h.send(alice, "  A question about the passive voice  "))
                .isInstanceOf(ChatBusyException.class);

        assertThat(h.used(alice)).isEqualTo(USED_BEFORE);
        assertThat(h.server.requestCount()).isEqualTo(1);
        Map<String, Object> total = data.dailyTotals(ChatFlowHarness.DAY);
        assertThat(total).containsEntry("messages", 1L).containsEntry("failed", 1L).containsEntry("ok", 0L)
                .containsEntry("tokens_in", (long) tokensIn).containsEntry("tokens_out", (long) tokensOut);
        assertThat(data.dailyRows()).singleElement()
                .satisfies(row -> assertThat(row.get("model")).isEqualTo(ChatFlowHarness.MODEL));
    }

    @Test
    void afterAFailureTheStudentIsNotLockedOutAndCanAskAgain() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(500, "");
        h.server.enqueue(200, GeminiResponses.onTopic("second try works"));
        assertThatThrownBy(() -> h.send(alice, "question")).isInstanceOf(ChatBusyException.class);

        ChatReply reply = h.send(alice, "question");

        assertThat(reply.markdown()).isEqualTo("second try works");
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(h.server.requestCount()).isEqualTo(2);
    }

    @Test
    void aFailureNeverEatsTheLastMessageOfTheDay() throws IOException {
        ChatFlowHarness h = start(p -> p.setDailyLimit(1));
        h.server.enqueue(500, "");
        h.server.enqueue(200, GeminiResponses.onTopic("still got my one message"));
        assertThatThrownBy(() -> h.send(alice, "question")).isInstanceOf(ChatBusyException.class);

        assertThat(h.send(alice, "question").markdown()).isEqualTo("still got my one message");
    }

    // ---- counting fails ----------------------------------------------------------------------------------

    @Test
    void aBrokenDailyCounterNeverBreaksTheReplyOrTheQuota() throws IOException {
        ChatFlowHarness h = start();
        h.server.enqueue(200, GeminiResponses.onTopic("answer despite the broken counter"));
        data.execute("ALTER TABLE chat_usage_daily ADD CONSTRAINT boom_no_inserts CHECK (false) NOT VALID");
        ChatReply reply;
        try {
            reply = h.send(alice, "question");
        } finally {
            data.execute("ALTER TABLE chat_usage_daily DROP CONSTRAINT boom_no_inserts");
        }

        assertThat(reply.markdown()).isEqualTo("answer despite the broken counter");
        assertThat(reply.onTopic()).isTrue();
        assertThat(h.used(alice)).isEqualTo(1);
        assertThat(data.dailyRows()).isEmpty();
    }
}
