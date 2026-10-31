package com.pte.chat.internal.client;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Small contract checks for the value types the service layer (Phase 3) will lean on. */
class ChatAiModelTypesTest {

    @Test
    void failureReasonsAreExactlyTheTenClassifications() {
        assertThat(Arrays.stream(ChatAiFailureReason.values()).map(Enum::name)).containsExactlyInAnyOrder(
                "TIMEOUT", "NETWORK_ERROR", "RATE_LIMITED", "UNAVAILABLE", "AUTH_FAILED", "BAD_REQUEST",
                "CONTENT_BLOCKED", "EMPTY_RESPONSE", "TRUNCATED", "INVALID_RESPONSE");
    }

    @Test
    void onlyTimeoutNetworkRateLimitAndUnavailableAreTransient() {
        Set<ChatAiFailureReason> transientReasons = EnumSet.of(ChatAiFailureReason.TIMEOUT,
                ChatAiFailureReason.NETWORK_ERROR, ChatAiFailureReason.RATE_LIMITED,
                ChatAiFailureReason.UNAVAILABLE);

        for (ChatAiFailureReason reason : ChatAiFailureReason.values()) {
            assertThat(reason.isTransient()).as(reason.name()).isEqualTo(transientReasons.contains(reason));
        }
    }

    @Test
    void noneUsageIsAllZeroWithoutAFinishReason() {
        assertThat(ChatAiUsage.NONE).isEqualTo(new ChatAiUsage(0, 0, 0, null));
        assertThat(ChatAiUsage.NONE.finishReason()).isNull();
    }

    @Test
    void exceptionWithoutUsageReportsNone() {
        ChatAiException error = new ChatAiException(ChatAiFailureReason.TIMEOUT, "timed out");

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TIMEOUT);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertThat(error.getMessage()).isEqualTo("timed out");
        assertThat(error).isInstanceOf(RuntimeException.class);
    }

    @Test
    void exceptionKeepsTheUsageItWasGiven() {
        ChatAiUsage usage = new ChatAiUsage(10, 800, 5, "MAX_TOKENS");

        ChatAiException error = new ChatAiException(ChatAiFailureReason.TRUNCATED, "cut off", usage);

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TRUNCATED);
        assertThat(error.usage()).isSameAs(usage);
    }

    @Test
    void exceptionWithACauseReportsNoneAndKeepsTheCause() {
        IllegalStateException cause = new IllegalStateException("boom");

        ChatAiException error = new ChatAiException(ChatAiFailureReason.NETWORK_ERROR, "unreachable", cause);

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.NETWORK_ERROR);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertThat(error.getCause()).isSameAs(cause);
    }

    @Test
    void exceptionUsageIsNeverNull() {
        ChatAiException error = new ChatAiException(ChatAiFailureReason.BAD_REQUEST, "bad", (ChatAiUsage) null);

        assertThat(error.usage()).isNotNull();
    }

    @Test
    void turnFactoriesSetTheGeminiRoles() {
        assertThat(ChatTurn.user("hi")).isEqualTo(new ChatTurn("user", "hi"));
        assertThat(ChatTurn.model("hello")).isEqualTo(new ChatTurn("model", "hello"));
    }

    @Test
    void answerIsAValueRecord() {
        ChatAiAnswer answer = new ChatAiAnswer(true, "md", new ChatAiUsage(1, 2, 3, "STOP"), "m-1");

        assertThat(answer.onTopic()).isTrue();
        assertThat(answer.markdown()).isEqualTo("md");
        assertThat(answer.usage().tokensOut()).isEqualTo(2);
        assertThat(answer.model()).isEqualTo("m-1");
        assertThat(answer).isEqualTo(new ChatAiAnswer(true, "md", new ChatAiUsage(1, 2, 3, "STOP"), "m-1"));
    }
}
