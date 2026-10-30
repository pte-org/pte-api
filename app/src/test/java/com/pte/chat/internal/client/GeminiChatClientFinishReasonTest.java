package com.pte.chat.internal.client;

import com.pte.chat.support.FakeGeminiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * A candidate that carries no (or a blank) finish reason cannot be trusted to be complete, so it is refused
 * like any other unusable answer: the call was paid for, the tokens are kept, and nothing is retried.
 */
class GeminiChatClientFinishReasonTest {

    private static final String ON_TOPIC_TEXT = "{\"is_on_topic\":true,\"markdown_content\":\"Use **has/have** + V3.\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private FakeGeminiServer server;
    private GeminiChatClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = FakeGeminiServer.start();
        client = new GeminiChatClient(HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), jsonMapper,
                server.baseUri(), "AIza-FAKE-KEY", "gemini-test-model", 0.2, 800, Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private String candidate(String finishReason) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", Map.of("role", "model", "parts", List.of(Map.of("text", ON_TOPIC_TEXT))));
        if (finishReason != null) {
            candidate.put("finishReason", finishReason);
        }
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokenCount", 11);
        usage.put("candidatesTokenCount", 22);
        usage.put("thoughtsTokenCount", 33);
        usage.put("totalTokenCount", 66);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidates", List.of(candidate));
        body.put("usageMetadata", usage);
        body.put("modelVersion", "gemini-test-model-001");
        return jsonMapper.writeValueAsString(body);
    }

    private ChatAiAnswer ask() {
        return client.generate("system", List.of(), "question");
    }

    @Test
    void aCandidateWithoutAFinishReasonIsAnInvalidResponseThatKeepsTheTokens() {
        server.enqueue(200, candidate(null));

        ChatAiException error = catchThrowableOfType(ChatAiException.class, this::ask);

        assertThat(error).isNotNull();
        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage().tokensIn()).isEqualTo(11);
        assertThat(error.usage().tokensOut()).isEqualTo(22);
        assertThat(error.usage().tokensThinking()).isEqualTo(33);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   "})
    void aBlankFinishReasonIsAnInvalidResponseThatKeepsTheTokens(String blank) {
        server.enqueue(200, candidate(blank));

        ChatAiException error = catchThrowableOfType(ChatAiException.class, this::ask);

        assertThat(error).isNotNull();
        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage().tokensIn()).isEqualTo(11);
        assertThat(error.usage().tokensOut()).isEqualTo(22);
        assertThat(error.usage().tokensThinking()).isEqualTo(33);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void stopStillSucceeds() {
        server.enqueue(200, candidate("STOP"));

        ChatAiAnswer answer = ask();

        assertThat(answer.onTopic()).isTrue();
        assertThat(answer.markdown()).isEqualTo("Use **has/have** + V3.");
        assertThat(answer.usage().finishReason()).isEqualTo("STOP");
        assertThat(answer.usage().tokensIn()).isEqualTo(11);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void theFailureMessageDoesNotEchoTheAnswerText() {
        server.enqueue(200, candidate(null));

        ChatAiException error = catchThrowableOfType(ChatAiException.class, this::ask);

        assertThat(String.valueOf(error.getMessage())).doesNotContain("has/have");
    }
}
