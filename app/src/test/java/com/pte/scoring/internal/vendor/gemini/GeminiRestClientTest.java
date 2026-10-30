package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.RecordingHttpServer;
import com.pte.scoring.internal.vendor.RecordingHttpServer.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiRestClientTest {

    private static final String KEY = "test-gemini-key";
    private static final String MODEL = "gemini-test-model";
    private static final String PROVIDER_DETAIL = "provider-detail-must-not-leak";
    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "OBJECT",
            "properties", Map.of("score", Map.of("type", "INTEGER")),
            "required", List.of("score"));
    private static final GeminiRequest TEXT_REQUEST =
            new GeminiRequest("You are a strict examiner.", "Score this answer.", SCHEMA, List.of());

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private RecordingHttpServer server;
    private GeminiRestClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = RecordingHttpServer.start();
        client = clientWithTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void sendsTheKeyInAHeaderAndTheSchemaInTheGenerationConfig() {
        server.enqueue(200, candidate("{\"score\": 3}", "gemini-2.5-flash-lite"));
        byte[] image = {1, 2, 3, 4};

        client.generate(new GeminiRequest("You are a strict examiner.", "Score this answer.", SCHEMA,
                List.of(new GeminiImage("image/png", image))));

        RecordedRequest request = server.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().getPath()).isEqualTo("/v1beta/models/gemini-test-model:generateContent");
        assertThat(request.uri().getQuery()).isNull();
        assertThat(request.header("x-goog-api-key")).isEqualTo(KEY);
        assertThat(request.header("Content-Type")).startsWith("application/json");

        JsonNode body = jsonMapper.readTree(request.body());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText())
                .isEqualTo("You are a strict examiner.");
        JsonNode parts = body.path("contents").path(0).path("parts");
        assertThat(body.path("contents").path(0).path("role").asText()).isEqualTo("user");
        assertThat(parts.path(0).path("inlineData").path("mimeType").asText()).isEqualTo("image/png");
        assertThat(parts.path(0).path("inlineData").path("data").asText())
                .isEqualTo(Base64.getEncoder().encodeToString(image));
        assertThat(parts.path(1).path("text").asText()).isEqualTo("Score this answer.");
        JsonNode config = body.path("generationConfig");
        assertThat(config.path("temperature").asDouble()).isZero();
        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");
        assertThat(config.path("responseSchema").path("type").asText()).isEqualTo("OBJECT");
        assertThat(config.path("responseSchema").path("required").path(0).asText()).isEqualTo("score");
    }

    @Test
    void guardedClientRetriesATransientFailureOnce() {
        server.enqueue(503, PROVIDER_DETAIL);
        server.enqueue(200, candidate("{\"score\": 3}", "gemini-2.5-flash-lite"));
        List<Duration> waits = new ArrayList<>();
        GeminiRestClient guarded = new GeminiRestClient(httpClient, jsonMapper, server.uri(""), KEY, MODEL,
                Duration.ofSeconds(5), new ProviderGuard("Gemini", 3, 5, com.pte.TestClocks.fixed(), waits::add));

        GeminiResponse response = guarded.generate(TEXT_REQUEST);

        assertThat(response.content().path("score").asInt()).isEqualTo(3);
        assertThat(server.requests()).hasSize(2);
        assertThat(waits).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void returnsTheStructuredJsonAndTheServedModelVersion() {
        server.enqueue(200, candidate("{\"score\": 3, \"feedback\": \"clear\"}", "gemini-2.5-flash-lite"));

        GeminiResponse response = client.generate(TEXT_REQUEST);

        assertThat(response.content().path("score").asInt()).isEqualTo(3);
        assertThat(response.content().path("feedback").asText()).isEqualTo("clear");
        assertThat(response.modelVersion()).isEqualTo("gemini-2.5-flash-lite");
    }

    @Test
    void fallsBackToTheConfiguredModelWhenNoVersionIsReported() {
        server.enqueue(200, """
                {"candidates":[{"content":{"parts":[{"text":"{\\"score\\": 1}"}]},"finishReason":"STOP"}]}
                """);

        assertThat(client.generate(TEXT_REQUEST).modelVersion()).isEqualTo(MODEL);
    }

    @Test
    void ignoresThoughtParts() {
        server.enqueue(200, """
                {"candidates":[{"content":{"parts":[
                  {"text":"thinking out loud","thought":true},
                  {"text":"{\\"score\\": 2}"}]},"finishReason":"STOP"}],
                 "modelVersion":"gemini-x"}
                """);

        assertThat(client.generate(TEXT_REQUEST).content().path("score").asInt()).isEqualTo(2);
    }

    @Test
    void rateLimitCarriesTheRetryDelayFromRetryInfo() {
        server.enqueue(429, """
                {"error":{"code":429,"message":"%s","status":"RESOURCE_EXHAUSTED","details":[
                  {"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[]},
                  {"@type":"type.googleapis.com/google.rpc.RetryInfo","retryDelay":"17s"}]}}
                """.formatted(PROVIDER_DETAIL));

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RATE_LIMITED);
                    assertThat(ex.isTransient()).isTrue();
                    assertThat(ex.getRetryAfter()).isEqualTo(Duration.ofSeconds(17));
                    assertThat(ex.getMessage()).doesNotContain(PROVIDER_DETAIL);
                });
    }

    @Test
    void invalidApiKeyIsAPermanentAuthFailure() {
        server.enqueue(400, """
                {"error":{"code":400,"message":"%s","status":"INVALID_ARGUMENT","details":[
                  {"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID",
                   "domain":"googleapis.com"}]}}
                """.formatted(PROVIDER_DETAIL));

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_AUTH_FAILED);
                    assertThat(ex.isTransient()).isFalse();
                    assertThat(ex.getMessage()).doesNotContain(PROVIDER_DETAIL);
                });
    }

    // Without an API_KEY_INVALID detail a 400 stays a bad request.
    @ParameterizedTest
    @CsvSource({"400, PROVIDER_BAD_REQUEST", "503, PROVIDER_UNAVAILABLE"})
    void classifiesHttpFailuresWithoutCopyingTheBody(int status, AiFailureReason reason) {
        server.enqueue(status, "{\"error\":{\"message\":\"" + PROVIDER_DETAIL + "\"}}");

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(reason);
                    assertThat(ex.getMessage()).contains(reason.name()).doesNotContain(PROVIDER_DETAIL);
                });
    }

    @Test
    void blockedPromptIsAPermanentContentBlock() {
        server.enqueue(200, "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}");

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_CONTENT_BLOCKED);
                    assertThat(ex.isTransient()).isFalse();
                });
    }

    @Test
    void safetyStoppedCandidateIsAPermanentContentBlock() {
        server.enqueue(200, "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}");

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_CONTENT_BLOCKED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Sure! Here is the score " + PROVIDER_DETAIL, "[1, 2]", ""})
    void textThatIsNotAJsonObjectIsAPermanentInvalidResponse(String text) {
        server.enqueue(200, candidate(text, "gemini-x"));

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID);
                    assertThat(ex.isTransient()).isFalse();
                    assertThat(ex.getMessage()).doesNotContain(PROVIDER_DETAIL);
                    assertThat(ex.getCause()).isNull();
                });
    }

    @Test
    void missingCandidatesIsAPermanentInvalidResponse() {
        server.enqueue(200, "{\"candidates\":[]}");

        assertThatThrownBy(() -> client.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class,
                        ex -> assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_RESPONSE_INVALID));
    }

    @Test
    void slowProviderTimesOutAsTransient() {
        GeminiRestClient impatient = clientWithTimeout(Duration.ofMillis(200));
        server.enqueueDelayed(1_000, 200, candidate("{\"score\": 1}", "gemini-x"));

        assertThatThrownBy(() -> impatient.generate(TEXT_REQUEST))
                .isInstanceOfSatisfying(AiProviderException.class, ex -> {
                    assertThat(ex.getReason()).isEqualTo(AiFailureReason.PROVIDER_TIMEOUT);
                    assertThat(ex.isTransient()).isTrue();
                });
    }

    private GeminiRestClient clientWithTimeout(Duration timeout) {
        return new GeminiRestClient(httpClient, jsonMapper, server.uri(""), KEY, MODEL, timeout);
    }

    private String candidate(String text, String modelVersion) {
        Map<String, Object> response = Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of("role", "model", "parts", List.of(Map.of("text", text))),
                        "finishReason", "STOP")),
                "modelVersion", modelVersion);
        return jsonMapper.writeValueAsString(response);
    }
}
