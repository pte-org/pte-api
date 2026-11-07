package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.config.QuestionGenerationConfig;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Drives the real gateway bean (real HTTP client, no redirects) against a local server. */
class NineRouterLlmGatewayTest {

    private static final String KEY = "test-key-not-a-secret";
    private static final String OK_BODY =
            "{\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}],\"usage\":{\"total_tokens\":42}}";
    private static final String PROMPT = "super secret prompt text";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    record Reply(int status, String body, String location, long delayMs) {
        static Reply ok() {
            return new Reply(200, OK_BODY, null, 0);
        }

        static Reply status(int status) {
            return new Reply(status, "{\"error\":\"provider says: " + PROMPT + " and " + KEY + "\"}", null, 0);
        }
    }

    record Seen(String path, String authorization, String body) {
    }

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<Seen> seen = new CopyOnWriteArrayList<>();
    private QuestionGenerationProperties properties;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.createContext("/", this::handle);
        server.start();
        properties = new QuestionGenerationProperties();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/");
        properties.getLlm().setApiKey(KEY);
        properties.getLlm().setGeneratorModel("gen-model");
        properties.getLlm().setReviewerModel("rev-model");
        properties.getLlm().setRetryDelay(Duration.ZERO);
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        seen.add(new Seen(exchange.getRequestURI().getPath(), exchange.getRequestHeaders().getFirst("Authorization"),
                body));
        Reply reply;
        synchronized (replies) {
            reply = replies.size() > 1 ? replies.poll() : replies.peek();
        }
        try {
            Thread.sleep(reply.delayMs());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        if (reply.location() != null) {
            exchange.getResponseHeaders().add("Location", reply.location());
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private NineRouterLlmGateway gateway(Reply... script) {
        synchronized (replies) {
            replies.addAll(List.of(script));
        }
        return new QuestionGenerationConfig().nineRouterLlmGateway(properties, JSON);
    }

    @Test
    void sendsModelTemperatureJsonModeAndBearerKeyAndReturnsTheContent() throws Exception {
        NineRouterLlmGateway gateway = gateway(Reply.ok());

        String content = gateway.completeJson(LlmRole.GENERATOR, "system text", PROMPT);

        assertThat(content).isEqualTo("{\"a\":1}");
        assertThat(seen).hasSize(1);
        Seen request = seen.get(0);
        assertThat(request.path()).isEqualTo("/v1/chat/completions");
        assertThat(request.authorization()).isEqualTo("Bearer " + KEY);
        JsonNode body = JSON.readTree(request.body());
        assertThat(body.path("model").asText()).isEqualTo("gen-model");
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.8);
        assertThat(body.path("response_format").path("type").asText()).isEqualTo("json_object");
        assertThat(body.path("stream").asBoolean(true)).isFalse();
        assertThat(body.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(body.path("messages").path(1).path("content").asText()).isEqualTo(PROMPT);
    }

    @Test
    void theReviewerUsesItsOwnModelAndTemperatureZero() throws Exception {
        NineRouterLlmGateway gateway = gateway(Reply.ok());

        gateway.completeJson(LlmRole.REVIEWER, "system", "user");

        JsonNode body = JSON.readTree(seen.get(0).body());
        assertThat(body.path("model").asText()).isEqualTo("rev-model");
        assertThat(body.path("temperature").asDouble()).isZero();
    }

    @Test
    void stripsAMarkdownFenceAroundTheJson() {
        String fenced = "{\"choices\":[{\"message\":{\"content\":\"```json\\n{\\\"a\\\":2}\\n```\"}}]}";
        NineRouterLlmGateway gateway = gateway(new Reply(200, fenced, null, 0));

        assertThat(gateway.completeJson(LlmRole.GENERATOR, "s", "u")).isEqualTo("{\"a\":2}");
    }

    @Test
    void anAuthFailureIsNotRetried() {
        NineRouterLlmGateway gateway = gateway(Reply.status(401));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", PROMPT))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.AUTH));
        assertThat(seen).hasSize(1);
    }

    @Test
    void aRateLimitIsRetriedOnceAndThenSucceeds() {
        NineRouterLlmGateway gateway = gateway(Reply.status(429), Reply.ok());

        assertThat(gateway.completeJson(LlmRole.GENERATOR, "s", "u")).isEqualTo("{\"a\":1}");
        assertThat(seen).hasSize(2);
    }

    @Test
    void aServerErrorIsRetriedOnceThenReportedAsUnavailable() {
        NineRouterLlmGateway gateway = gateway(Reply.status(503));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.UNAVAILABLE));
        assertThat(seen).hasSize(2);
    }

    @Test
    void anotherClientErrorIsRejectedWithoutRetry() {
        NineRouterLlmGateway gateway = gateway(Reply.status(400));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.REJECTED));
        assertThat(seen).hasSize(1);
    }

    @Test
    void anEmptyMessageIsRetriedOnceThenReportedAsAnEmptyResponse() {
        NineRouterLlmGateway gateway = gateway(new Reply(200, "{\"choices\":[{\"message\":{\"content\":\"  \"}}]}",
                null, 0));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.EMPTY_RESPONSE));
        assertThat(seen).hasSize(2);
    }

    @Test
    void anEmptyMessageFollowedByContentSucceedsOnTheRetry() {
        NineRouterLlmGateway gateway = gateway(
                new Reply(200, "{\"choices\":[{\"message\":{\"content\":\"\"}}]}", null, 0), Reply.ok());

        assertThat(gateway.completeJson(LlmRole.GENERATOR, "s", "u")).isEqualTo("{\"a\":1}");
        assertThat(seen).hasSize(2);
    }

    @Test
    void aRedirectIsNeverFollowedSoTheKeyCannotLeaveTheHost() {
        String elsewhere = "http://127.0.0.1:" + server.getAddress().getPort() + "/elsewhere";
        NineRouterLlmGateway gateway = gateway(new Reply(302, "", elsewhere, 0));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.REJECTED));
        assertThat(seen).extracting(Seen::path).containsExactly("/v1/chat/completions");
    }

    @Test
    void aSlowProviderTimesOutAsUnavailable() {
        properties.getLlm().setReadTimeout(Duration.ofMillis(200));
        NineRouterLlmGateway gateway = gateway(new Reply(200, OK_BODY, null, 1500));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", "u"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.UNAVAILABLE));
    }

    @Test
    void failuresNeverCarryThePromptTheProviderBodyOrTheKey() {
        NineRouterLlmGateway gateway = gateway(Reply.status(400));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "s", PROMPT))
                .satisfies(failure -> {
                    assertThat(failure.getMessage()).doesNotContain(PROMPT).doesNotContain(KEY);
                    assertThat(failure.getCause()).isNull();
                });
    }
}
