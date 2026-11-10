package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.config.QuestionGenerationConfig;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.live.GenerationLiveFeed;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The gateway while an item is watched: it asks for a stream and hands every piece of text to the item's sink. */
class NineRouterLlmGatewayStreamTest {

    private final com.pte.itembank.internal.generation.usage.GenerationUsageRecorder usageRecorder =
            org.mockito.Mockito.mock(com.pte.itembank.internal.generation.usage.GenerationUsageRecorder.class);

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final UUID JOB = UUID.randomUUID();
    private static final String STREAM = """
            data: {"choices":[{"delta":{"role":"assistant"}}]}

            data: {"choices":[{"delta":{"content":"{\\"topic\\":"}}]}

            : keep-alive
            data: {"choices":[{"delta":{"content":"\\"Bees\\"}"}}],"usage":{"total_tokens":7}}

            data: [DONE]

            """;

    record Reply(int status, String contentType, String body) {
    }

    /** Records what the gateway told the live view, in order. */
    static final class RecordingSink implements TokenSink {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override
        public void begin() {
            calls.add("begin");
        }

        @Override
        public void accept(String delta) {
            calls.add(delta);
        }
    }

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final RecordingSink sink = new RecordingSink();
    private QuestionGenerationProperties properties;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newFixedThreadPool(2));
        server.createContext("/", this::handle);
        server.start();
        properties = new QuestionGenerationProperties();
        properties.getLlm().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        properties.getLlm().setApiKey("test-key-not-a-secret");
        properties.getLlm().setGeneratorModel("gen-model");
        properties.getLlm().setReviewerModel("rev-model");
        properties.getLlm().setRetryDelay(Duration.ZERO);
        GenerationLiveFeed feed = mock(GenerationLiveFeed.class);
        when(feed.sink(any(), anyInt(), any())).thenReturn(sink);
        GenerationLiveContext.open(feed, JOB, 0);
    }

    @AfterEach
    void stop() {
        GenerationLiveContext.close();
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        Reply reply;
        synchronized (replies) {
            reply = replies.size() > 1 ? replies.poll() : replies.peek();
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", reply.contentType());
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private NineRouterLlmGateway gateway(Reply... script) {
        synchronized (replies) {
            replies.addAll(List.of(script));
        }
        return new QuestionGenerationConfig().nineRouterLlmGateway(properties, JSON, usageRecorder);
    }

    private static Reply stream() {
        return new Reply(200, "text/event-stream", STREAM);
    }

    @Test
    void aWatchedItemAsksForAStreamAndHandsEveryPieceOfTextToTheSink() {
        String answer = gateway(stream()).completeJson(LlmRole.GENERATOR, "system", "user");

        assertThat(answer).isEqualTo("{\"topic\":\"Bees\"}");
        assertThat(sink.calls).containsExactly("begin", "{\"topic\":", "\"Bees\"}");
        assertThat(JSON.readTree(bodies.get(0)).path("stream").asBoolean()).isTrue();
    }

    @Test
    void aProviderThatIgnoresTheStreamIsReadAsOneAnswer() {
        Reply plain = new Reply(200, "application/json",
                "{\"choices\":[{\"message\":{\"content\":\"{\\\"a\\\":1}\"}}],\"usage\":{\"total_tokens\":3}}");

        String answer = gateway(plain).completeJson(LlmRole.REVIEWER, "system", "user");

        assertThat(answer).isEqualTo("{\"a\":1}");
        assertThat(sink.calls).containsExactly("begin", "{\"a\":1}");
    }

    @Test
    void aRetriedCallStartsTheTextAgain() {
        String answer = gateway(new Reply(503, "application/json", "{\"error\":\"busy\"}"), stream())
                .completeJson(LlmRole.GENERATOR, "system", "user");

        assertThat(answer).isEqualTo("{\"topic\":\"Bees\"}");
        assertThat(sink.calls).containsExactly("begin", "begin", "{\"topic\":", "\"Bees\"}");
    }

    @Test
    void aRejectedKeyIsClassifiedWithoutReadingTheBody() {
        NineRouterLlmGateway gateway = gateway(new Reply(401, "application/json", "{\"error\":\"secret text\"}"));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "system", "user"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.AUTH));
    }

    @Test
    void anErrorInsideTheStreamIsAProviderOutage() {
        NineRouterLlmGateway gateway = gateway(new Reply(200, "text/event-stream",
                "data: {\"error\":{\"message\":\"overloaded\"}}\n\n"));

        assertThatThrownBy(() -> gateway.completeJson(LlmRole.GENERATOR, "system", "user"))
                .isInstanceOfSatisfying(LlmProviderException.class,
                        failure -> assertThat(failure.getType()).isEqualTo(LlmFailureType.UNAVAILABLE));
    }

    @Test
    void outsideAnItemTheCallIsNotStreamed() {
        GenerationLiveContext.close();
        Reply plain = new Reply(200, "application/json", "{\"choices\":[{\"message\":{\"content\":\"{}\"}}]}");

        gateway(plain).completeJson(LlmRole.GENERATOR, "system", "user");

        assertThat(JSON.readTree(bodies.get(0)).path("stream").asBoolean()).isFalse();
        assertThat(sink.calls).isEmpty();
    }
}
