package com.pte.chat.internal.client;

import com.pte.chat.support.FakeGeminiServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

class OpenAiChatClientTest {
    private final JsonMapper mapper = JsonMapper.builder().build();
    private FakeGeminiServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = FakeGeminiServer.start();
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private OpenAiChatClient client(int maxTokens, Duration timeout) {
        return new OpenAiChatClient(HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build(),
                mapper, URI.create(server.baseUri() + "/v1/"), "fake-provider-key", "Capstone", 0.2, maxTokens, timeout);
    }

    private ChatAiAnswer ask(OpenAiChatClient client) {
        return client.generate("Return JSON with is_on_topic and markdown_content.",
                List.of(ChatTurn.user("Previous question"), ChatTurn.model("Previous answer")), "Current question");
    }

    private String response(String content, String finish) {
        return mapper.writeValueAsString(Map.of("model", "served-model", "choices", List.of(
                        Map.of("message", Map.of("content", content), "finish_reason", finish)),
                "usage", Map.of("prompt_tokens", 10, "completion_tokens", 30,
                        "completion_tokens_details", Map.of("reasoning_tokens", 8))));
    }

    @Test
    void usesSharedProviderProtocolAndPreservesProxyPrefixAndHistory() {
        server.enqueue(200, response("{\"is_on_topic\":true,\"markdown_content\":\"Hello **student**\"}", "stop"));

        ChatAiAnswer answer = ask(client(0, Duration.ofSeconds(5)));

        assertThat(answer.markdown()).isEqualTo("Hello **student**");
        assertThat(answer.model()).isEqualTo("served-model");
        assertThat(answer.usage()).isEqualTo(new ChatAiUsage(10, 22, 8, "stop"));
        assertThat(server.requestCount()).isEqualTo(1);
        var request = server.requests().getFirst();
        assertThat(request.uri().getPath()).isEqualTo("/v1/chat/completions");
        assertThat(request.uri().getQuery()).isNull();
        assertThat(request.header("Authorization")).isEqualTo("Bearer fake-provider-key");
        assertThat(request.header("x-goog-api-key")).isNull();
        var body = mapper.readTree(request.body());
        assertThat(body.path("model").asText()).isEqualTo("Capstone");
        assertThat(body.path("stream").asBoolean()).isFalse();
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.has("max_tokens")).isFalse();
        assertThat(body.path("messages").size()).isEqualTo(4);
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(2).path("role").asText()).isEqualTo("assistant");
        assertThat(body.path("messages").path(3).path("content").asText()).isEqualTo("Current question");
    }

    @Test
    void honorsConfiguredTokenCapAndOffTopicRefusal() {
        server.enqueue(200, response("{\"is_on_topic\":false,\"markdown_content\":\"\"}", "stop"));

        assertThat(ask(client(1200, Duration.ofSeconds(5))).onTopic()).isFalse();
        assertThat(mapper.readTree(server.requests().getFirst().body()).path("max_tokens").asInt()).isEqualTo(1200);
    }

    @ParameterizedTest
    @CsvSource({"401,AUTH_FAILED", "403,AUTH_FAILED", "408,TIMEOUT", "429,RATE_LIMITED",
            "500,UNAVAILABLE", "503,UNAVAILABLE", "400,BAD_REQUEST", "302,BAD_REQUEST"})
    void classifiesFailuresWithoutRetryOrPrivateProviderText(int status, ChatAiFailureReason reason) {
        server.enqueue(status, "private-provider-body fake-provider-key Current question");

        Throwable error = catchThrowable(() -> ask(client(0, Duration.ofSeconds(5))));

        assertThat(error).isInstanceOf(ChatAiException.class);
        assertThat(((ChatAiException) error).reason()).isEqualTo(reason);
        assertThat(error.getMessage()).doesNotContain("private-provider-body", "fake-provider-key", "Current question");
        assertThat(error.getCause()).isNull();
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"length,TRUNCATED", "content_filter,CONTENT_BLOCKED", "tool_calls,INVALID_RESPONSE"})
    void rejectsUnfinishedAnswersButPreservesUsage(String finish, ChatAiFailureReason reason) {
        server.enqueue(200, response("{}", finish));

        var error = (ChatAiException) catchThrowable(() -> ask(client(0, Duration.ofSeconds(5))));

        assertThat(error.reason()).isEqualTo(reason);
        assertThat(error.usage().tokensIn()).isEqualTo(10);
    }

    @Test
    void malformedOutputPreservesPaidUsageWithoutLeakingTheResponse() {
        server.enqueue(200, response("private-provider-body", "stop"));

        var error = (ChatAiException) catchThrowable(() -> ask(client(0, Duration.ofSeconds(5))));

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage().tokensOut()).isEqualTo(22);
        assertThat(error.getMessage()).doesNotContain("private-provider-body");
    }

    @Test
    void boundsStalledResponseBodiesWithoutRetry() {
        server.enqueueStalledBody(2000, 200, response("{}", "stop"));

        var error = (ChatAiException) catchThrowable(() -> ask(client(0, Duration.ofMillis(500))));

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TIMEOUT);
        assertThat(server.requestCount()).isEqualTo(1);
    }
}
