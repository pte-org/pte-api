package com.pte.chat.internal.client;

import com.pte.chat.support.FakeGeminiServer;
import com.pte.chat.support.FakeGeminiServer.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * One HTTP call per question, never a retry: every failure is classified once and handed to the caller.
 * The fake server binds loopback only, so no test needs the network or a real key.
 */
class GeminiChatClientTest {

    private static final String KEY = "AIza-FAKE-KEY-5d1c0e";
    private static final String MODEL = "gemini-test-model";
    private static final double TEMPERATURE = 0.2;
    private static final int MAX_OUTPUT_TOKENS = 800;
    private static final String SYSTEM = "You are the PTE study assistant. SYSTEM-MARKER-3b8";
    private static final String QUESTION = "QUESTION-MARKER-7c1 How do I use the present perfect?";
    private static final String HISTORY_USER = "HISTORY-USER-MARKER-4d2";
    private static final String HISTORY_MODEL = "HISTORY-MODEL-MARKER-8e6";
    private static final String PROVIDER_BODY = "PROVIDER-BODY-MARKER-91b";
    private static final String ON_TOPIC_TEXT = "{\"is_on_topic\":true,\"markdown_content\":\"Use **has/have** + V3.\"}";

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    private FakeGeminiServer server;
    private GeminiChatClient client;

    @BeforeEach
    void startServer() throws IOException {
        server = FakeGeminiServer.start();
        client = clientWithTimeout(Duration.ofSeconds(5));
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    private GeminiChatClient clientWithTimeout(Duration timeout) {
        return new GeminiChatClient(httpClient, jsonMapper, server.baseUri(), KEY, MODEL, TEMPERATURE,
                MAX_OUTPUT_TOKENS, timeout);
    }

    private static List<ChatTurn> history() {
        return List.of(ChatTurn.user(HISTORY_USER), ChatTurn.model(HISTORY_MODEL));
    }

    private ChatAiAnswer ask() {
        return client.generate(SYSTEM, history(), QUESTION);
    }

    // ---- response builders -------------------------------------------------------------------------------

    private String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    private static Map<String, Object> usage(int in, int out, Integer thinking) {
        Map<String, Object> usage = new LinkedHashMap<>();
        usage.put("promptTokenCount", in);
        usage.put("candidatesTokenCount", out);
        if (thinking != null) {
            usage.put("thoughtsTokenCount", thinking);
        }
        usage.put("totalTokenCount", in + out + (thinking == null ? 0 : thinking));
        return usage;
    }

    private String candidate(String text, String finishReason) {
        return candidate(List.of(Map.of("text", text)), finishReason, "gemini-test-model-001", usage(11, 22, 33));
    }

    private String candidate(List<Map<String, Object>> parts, String finishReason, String modelVersion,
                             Map<String, Object> usageMetadata) {
        Map<String, Object> candidate = new LinkedHashMap<>();
        candidate.put("content", Map.of("role", "model", "parts", parts));
        if (finishReason != null) {
            candidate.put("finishReason", finishReason);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("candidates", List.of(candidate));
        if (usageMetadata != null) {
            body.put("usageMetadata", usageMetadata);
        }
        if (modelVersion != null) {
            body.put("modelVersion", modelVersion);
        }
        return json(body);
    }

    private String candidateWithoutContent(String finishReason) {
        return json(Map.of(
                "candidates", List.of(Map.of("finishReason", finishReason)),
                "usageMetadata", usage(11, 0, null),
                "modelVersion", "gemini-test-model-001"));
    }

    // ---- request shape -----------------------------------------------------------------------------------

    @Test
    void sendsOnePostWithTheKeyInAHeaderOnly() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ask();

        assertThat(server.requestCount()).isEqualTo(1);
        RecordedRequest request = server.requests().getFirst();
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.uri().getPath()).isEqualTo("/v1beta/models/" + MODEL + ":generateContent");
        assertThat(request.uri().getRawQuery()).isNull();
        assertThat(request.uri().toString()).doesNotContain(KEY);
        assertThat(request.header("x-goog-api-key")).isEqualTo(KEY);
        assertThat(request.header("Content-Type")).startsWith("application/json");
        assertThat(request.body()).doesNotContain(KEY);
    }

    @Test
    void sendsSystemInstructionThenHistoryInOrderThenTheCurrentQuestion() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ask();

        JsonNode body = jsonMapper.readTree(server.requests().getFirst().body());
        assertThat(body.path("systemInstruction").path("parts").path(0).path("text").asText()).isEqualTo(SYSTEM);
        JsonNode contents = body.path("contents");
        assertThat(contents.size()).isEqualTo(3);
        assertThat(contents.path(0).path("role").asText()).isEqualTo("user");
        assertThat(contents.path(0).path("parts").path(0).path("text").asText()).isEqualTo(HISTORY_USER);
        assertThat(contents.path(1).path("role").asText()).isEqualTo("model");
        assertThat(contents.path(1).path("parts").path(0).path("text").asText()).isEqualTo(HISTORY_MODEL);
        assertThat(contents.path(2).path("role").asText()).isEqualTo("user");
        assertThat(contents.path(2).path("parts").path(0).path("text").asText()).isEqualTo(QUESTION);
    }

    @Test
    void sendsOnlyTheQuestionWhenThereIsNoHistory() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        client.generate(SYSTEM, List.of(), QUESTION);

        JsonNode contents = jsonMapper.readTree(server.requests().getFirst().body()).path("contents");
        assertThat(contents.size()).isEqualTo(1);
        assertThat(contents.path(0).path("role").asText()).isEqualTo("user");
        assertThat(contents.path(0).path("parts").path(0).path("text").asText()).isEqualTo(QUESTION);
    }

    @Test
    void pinsTemperatureTokenCapAndTheTwoFieldSchemaWithIsOnTopicFirst() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ask();

        JsonNode config = jsonMapper.readTree(server.requests().getFirst().body()).path("generationConfig");
        assertThat(config.path("temperature").asDouble()).isEqualTo(TEMPERATURE);
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(MAX_OUTPUT_TOKENS);
        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");

        JsonNode schema = config.path("responseSchema");
        assertThat(schema.path("type").asText()).isEqualTo("OBJECT");
        assertThat(schema.path("properties").path("is_on_topic").path("type").asText()).isEqualTo("BOOLEAN");
        assertThat(schema.path("properties").path("markdown_content").path("type").asText()).isEqualTo("STRING");
        assertThat(propertyNamesInOrder(schema.path("properties"))).containsExactly("is_on_topic", "markdown_content");
        assertThat(texts(schema.path("required"))).containsExactly("is_on_topic", "markdown_content");
        assertThat(texts(schema.path("propertyOrdering"))).containsExactly("is_on_topic", "markdown_content");
    }

    @Test
    void sendsNoThinkingFieldWhenNoLevelIsSet() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ask();

        JsonNode config = jsonMapper.readTree(server.requests().getFirst().body()).path("generationConfig");
        assertThat(config.has("thinkingConfig")).isFalse();
    }

    @Test
    void sendsTheThinkingLevelInsideGenerationConfigWhenOneIsSet() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));
        GeminiChatClient minimal = new GeminiChatClient(httpClient, jsonMapper, server.baseUri(), KEY, MODEL,
                TEMPERATURE, MAX_OUTPUT_TOKENS, Duration.ofSeconds(5), "minimal");

        minimal.generate(SYSTEM, history(), QUESTION);

        JsonNode config = jsonMapper.readTree(server.requests().getFirst().body()).path("generationConfig");
        assertThat(config.path("thinkingConfig").path("thinkingLevel").asText()).isEqualTo("minimal");
        assertThat(config.path("thinkingConfig").has("thinkingBudget")).isFalse();
        assertThat(config.path("maxOutputTokens").asInt()).isEqualTo(MAX_OUTPUT_TOKENS);
    }

    @SuppressWarnings("unchecked")
    private List<String> propertyNamesInOrder(JsonNode properties) {
        return new ArrayList<>(jsonMapper.convertValue(properties, LinkedHashMap.class).keySet());
    }

    private static List<String> texts(JsonNode array) {
        List<String> values = new ArrayList<>();
        array.forEach(item -> values.add(item.asText()));
        return values;
    }

    // ---- successful answers ------------------------------------------------------------------------------

    @Test
    void returnsTheOnTopicAnswerWithUsageFinishReasonAndServedModel() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ChatAiAnswer answer = ask();

        assertThat(answer.onTopic()).isTrue();
        assertThat(answer.markdown()).isEqualTo("Use **has/have** + V3.");
        assertThat(answer.usage().tokensIn()).isEqualTo(11);
        assertThat(answer.usage().tokensOut()).isEqualTo(22);
        assertThat(answer.usage().tokensThinking()).isEqualTo(33);
        assertThat(answer.usage().finishReason()).isEqualTo("STOP");
        assertThat(answer.model()).isEqualTo("gemini-test-model-001");
    }

    @Test
    void fallsBackToTheConfiguredModelWhenNoModelVersionIsReported() {
        server.enqueue(200, candidate(List.of(Map.of("text", ON_TOPIC_TEXT)), "STOP", null, usage(1, 2, 3)));

        assertThat(ask().model()).isEqualTo(MODEL);
    }

    @Test
    void missingUsageCountsDefaultToZero() {
        server.enqueue(200, candidate(List.of(Map.of("text", ON_TOPIC_TEXT)), "STOP", null, null));

        ChatAiAnswer answer = ask();

        assertThat(answer.usage().tokensIn()).isZero();
        assertThat(answer.usage().tokensOut()).isZero();
        assertThat(answer.usage().tokensThinking()).isZero();
        assertThat(answer.usage().finishReason()).isEqualTo("STOP");
    }

    @Test
    void missingThoughtsCountDefaultsToZeroWhileOtherCountsAreRead() {
        server.enqueue(200, candidate(List.of(Map.of("text", ON_TOPIC_TEXT)), "STOP", null, usage(7, 9, null)));

        ChatAiAnswer answer = ask();

        assertThat(answer.usage().tokensIn()).isEqualTo(7);
        assertThat(answer.usage().tokensOut()).isEqualTo(9);
        assertThat(answer.usage().tokensThinking()).isZero();
    }

    @Test
    void skipsThoughtPartsAndConcatenatesTheRemainingTextParts() {
        List<Map<String, Object>> parts = List.of(
                Map.of("text", "THOUGHT-MARKER this must not reach the parser", "thought", true),
                Map.of("text", "{\"is_on_topic\":true,"),
                Map.of("text", "\"markdown_content\":\"Joined answer\"}"));
        server.enqueue(200, candidate(parts, "STOP", "gemini-test-model-001", usage(1, 2, 3)));

        ChatAiAnswer answer = ask();

        assertThat(answer.onTopic()).isTrue();
        assertThat(answer.markdown()).isEqualTo("Joined answer");
    }

    @Test
    void acceptsModelOutputWrappedInAJsonFence() {
        server.enqueue(200, candidate("```json\n" + ON_TOPIC_TEXT + "\n```", "STOP"));

        assertThat(ask().markdown()).isEqualTo("Use **has/have** + V3.");
    }

    @Test
    void offTopicAnswerCarriesNoMarkdown() {
        server.enqueue(200, candidate("{\"is_on_topic\":false,\"markdown_content\":\"here is a cake recipe\"}", "STOP"));

        ChatAiAnswer answer = ask();

        assertThat(answer.onTopic()).isFalse();
        assertThat(answer.markdown()).isEmpty();
        assertThat(answer.usage().tokensIn()).isEqualTo(11);
    }

    // ---- failure classification --------------------------------------------------------------------------

    private static final String API_KEY_INVALID_BODY = "{\"error\":{\"code\":400,\"message\":\"API key not valid. "
            + PROVIDER_BODY + "\",\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"@type\":"
            + "\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"API_KEY_INVALID\","
            + "\"domain\":\"googleapis.com\"}]}}";

    private static String errorBody(int code, String status) {
        return "{\"error\":{\"code\":" + code + ",\"message\":\"" + PROVIDER_BODY + "\",\"status\":\"" + status + "\"}}";
    }

    static Stream<Arguments> httpFailures() {
        return Stream.of(
                Arguments.of("400 API_KEY_INVALID", 400, API_KEY_INVALID_BODY, ChatAiFailureReason.AUTH_FAILED),
                Arguments.of("400 invalid argument", 400, errorBody(400, "INVALID_ARGUMENT"),
                        ChatAiFailureReason.BAD_REQUEST),
                Arguments.of("400 with a non-JSON body", 400, "<html>" + PROVIDER_BODY + "</html>",
                        ChatAiFailureReason.BAD_REQUEST),
                Arguments.of("401", 401, errorBody(401, "UNAUTHENTICATED"), ChatAiFailureReason.AUTH_FAILED),
                Arguments.of("403", 403, errorBody(403, "PERMISSION_DENIED"), ChatAiFailureReason.AUTH_FAILED),
                Arguments.of("404", 404, errorBody(404, "NOT_FOUND"), ChatAiFailureReason.BAD_REQUEST),
                Arguments.of("408", 408, errorBody(408, "DEADLINE_EXCEEDED"), ChatAiFailureReason.TIMEOUT),
                Arguments.of("429", 429, errorBody(429, "RESOURCE_EXHAUSTED"), ChatAiFailureReason.RATE_LIMITED),
                Arguments.of("500", 500, errorBody(500, "INTERNAL"), ChatAiFailureReason.UNAVAILABLE),
                Arguments.of("502", 502, "<html>" + PROVIDER_BODY + "</html>", ChatAiFailureReason.UNAVAILABLE),
                Arguments.of("503", 503, errorBody(503, "UNAVAILABLE"), ChatAiFailureReason.UNAVAILABLE),
                Arguments.of("504", 504, errorBody(504, "DEADLINE_EXCEEDED"), ChatAiFailureReason.UNAVAILABLE));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("httpFailures")
    void classifiesHttpErrorsOnceWithoutRetryOrLeak(String name, int status, String body,
                                                    ChatAiFailureReason expected) {
        server.enqueue(status, body);
        // A success queued behind the failure would reveal any retry.
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(expected);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of("non-JSON 200 body", "<html>" + PROVIDER_BODY + "</html>"),
                Arguments.of("plain text 200 body", "oops " + PROVIDER_BODY),
                Arguments.of("truncated JSON 200 body", "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\""
                        + PROVIDER_BODY));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("invalidBodies")
    void aBodyThatIsNotJsonIsAnInvalidResponse(String name, String body) {
        server.enqueue(200, body);

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @Test
    void aBlockedPromptIsContentBlockedAndKeepsTheReportedPromptTokens() {
        server.enqueue(200, json(Map.of(
                "promptFeedback", Map.of("blockReason", "SAFETY"),
                "usageMetadata", Map.of("promptTokenCount", 9, "totalTokenCount", 9))));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.CONTENT_BLOCKED);
        assertThat(error.usage().tokensIn()).isEqualTo(9);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @Test
    void aBlockedPromptWinsOverAnyCandidate() {
        server.enqueue(200, json(Map.of(
                "promptFeedback", Map.of("blockReason", "OTHER"),
                "candidates", List.of(Map.of("content", Map.of("role", "model",
                        "parts", List.of(Map.of("text", ON_TOPIC_TEXT))), "finishReason", "STOP")))));

        assertThat(failure().reason()).isEqualTo(ChatAiFailureReason.CONTENT_BLOCKED);
    }

    @Test
    void emptyCandidatesIsAnEmptyResponse() {
        server.enqueue(200, json(Map.of("candidates", List.of(), "usageMetadata", usage(5, 0, null))));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.EMPTY_RESPONSE);
        assertThat(error.usage().tokensIn()).isEqualTo(5);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void missingCandidatesIsAnEmptyResponse() {
        server.enqueue(200, json(Map.of("modelVersion", "gemini-test-model-001")));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.EMPTY_RESPONSE);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    @Test
    void anEmptyJsonObjectIsAnEmptyResponse() {
        server.enqueue(200, "{}");

        assertThat(failure().reason()).isEqualTo(ChatAiFailureReason.EMPTY_RESPONSE);
    }

    static Stream<String> blockingFinishReasons() {
        return Stream.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION");
    }

    @ParameterizedTest
    @MethodSource("blockingFinishReasons")
    void blockingFinishReasonsAreContentBlockedEvenWithoutContent(String finishReason) {
        server.enqueue(200, candidateWithoutContent(finishReason));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.CONTENT_BLOCKED);
        assertThat(error.usage().finishReason()).isEqualTo(finishReason);
        assertThat(error.usage().tokensIn()).isEqualTo(11);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @ParameterizedTest
    @MethodSource("blockingFinishReasons")
    void blockingFinishReasonsAreContentBlockedEvenWhenTheModelWroteValidText(String finishReason) {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, finishReason));

        assertThat(failure().reason()).isEqualTo(ChatAiFailureReason.CONTENT_BLOCKED);
    }

    @Test
    void maxTokensIsTruncatedAndCarriesTheRealUsage() {
        String partial = "{\"is_on_topic\":true,\"markdown_content\":\"" + PROVIDER_BODY + " half a sent";
        server.enqueue(200, candidate(List.of(Map.of("text", partial)), "MAX_TOKENS", "gemini-test-model-001",
                usage(10, 800, 5)));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TRUNCATED);
        assertThat(error.usage()).isEqualTo(new ChatAiUsage(10, 800, 5, "MAX_TOKENS"));
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @Test
    void maxTokensIsTruncatedEvenWhenTheCutTextHappensToParse() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "MAX_TOKENS"));

        assertThat(failure().reason()).isEqualTo(ChatAiFailureReason.TRUNCATED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"OTHER", "MALFORMED_FUNCTION_CALL"})
    void anyOtherNonStopFinishReasonIsAnInvalidResponse(String finishReason) {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, finishReason));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage().finishReason()).isEqualTo(finishReason);
        assertThat(server.requestCount()).isEqualTo(1);
    }

    static Stream<Arguments> unusableModelText() {
        return Stream.of(
                Arguments.of("prose instead of JSON", "Sure thing! " + PROVIDER_BODY),
                Arguments.of("JSON cut off mid string", "{\"is_on_topic\":true,\"markdown_content\":\"" + PROVIDER_BODY),
                Arguments.of("on topic with empty markdown", "{\"is_on_topic\":true,\"markdown_content\":\"\"}"),
                Arguments.of("on topic with blank markdown", "{\"is_on_topic\":true,\"markdown_content\":\"   \"}"),
                Arguments.of("on topic with missing markdown", "{\"is_on_topic\":true}"),
                Arguments.of("missing is_on_topic", "{\"markdown_content\":\"" + PROVIDER_BODY + "\"}"),
                Arguments.of("is_on_topic as a string",
                        "{\"is_on_topic\":\"true\",\"markdown_content\":\"" + PROVIDER_BODY + "\"}"),
                Arguments.of("JSON array", "[" + ON_TOPIC_TEXT + "]"),
                Arguments.of("trailing garbage", ON_TOPIC_TEXT + " trailing " + PROVIDER_BODY));
    }

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("unusableModelText")
    void modelTextThatFailsTheParserIsAnInvalidResponseWithUsage(String name, String modelText) {
        server.enqueue(200, candidate(modelText, "STOP"));

        ChatAiException error = failure();

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.INVALID_RESPONSE);
        assertThat(error.usage()).isEqualTo(new ChatAiUsage(11, 22, 33, "STOP"));
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    // ---- network failures --------------------------------------------------------------------------------

    @Test
    void aRefusedConnectionIsANetworkError() {
        URI dead = server.baseUri();
        server.close();
        GeminiChatClient orphan = new GeminiChatClient(httpClient, jsonMapper, dead, KEY, MODEL, TEMPERATURE,
                MAX_OUTPUT_TOKENS, Duration.ofSeconds(5));

        ChatAiException error = catchFailure(orphan);

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.NETWORK_ERROR);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertNothingLeaked(error);
    }

    @Test
    void aSlowServerTimesOutOnceWithoutRetry() {
        server.enqueueDelayed(3_000, 200, candidate(ON_TOPIC_TEXT, "STOP"));
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));
        GeminiChatClient impatient = clientWithTimeout(Duration.ofMillis(300));

        ChatAiException error = catchFailure(impatient);

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TIMEOUT);
        assertThat(error.usage()).isEqualTo(ChatAiUsage.NONE);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @Test
    void aStalledResponseBodyTimesOutOnceWithoutRetry() {
        server.enqueueStalledBody(3_000, 200, candidate(ON_TOPIC_TEXT, "STOP"));
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));
        GeminiChatClient impatient = clientWithTimeout(Duration.ofMillis(300));

        ChatAiException error = catchFailure(impatient);

        assertThat(error.reason()).isEqualTo(ChatAiFailureReason.TIMEOUT);
        assertThat(server.requestCount()).isEqualTo(1);
        assertNothingLeaked(error);
    }

    @Test
    void aSuccessfulCallStillMakesExactlyOneRequest() {
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));
        server.enqueue(200, candidate(ON_TOPIC_TEXT, "STOP"));

        ask();

        assertThat(server.requestCount()).isEqualTo(1);
    }

    // ---- helpers -----------------------------------------------------------------------------------------

    private ChatAiException failure() {
        return catchFailure(client);
    }

    private ChatAiException catchFailure(GeminiChatClient target) {
        Throwable thrown = catchThrowable(
                () -> target.generate(SYSTEM, history(), QUESTION));
        assertThat(thrown).isInstanceOf(ChatAiException.class);
        return (ChatAiException) thrown;
    }

    /** Neither the message nor any cause may carry the key, the question, history text or provider body. */
    private static void assertNothingLeaked(Throwable error) {
        List<String> secrets = List.of(KEY, QUESTION, "QUESTION-MARKER-7c1", HISTORY_USER, HISTORY_MODEL,
                PROVIDER_BODY, "THOUGHT-MARKER", SYSTEM);
        for (Throwable current = error; current != null; current = current.getCause()) {
            String text = current + " " + current.getMessage() + " " + current.getLocalizedMessage();
            for (String secret : secrets) {
                assertThat(text).as("%s leaks '%s'", current.getClass().getSimpleName(), secret).doesNotContain(secret);
            }
            if (current.getCause() == current) {
                break;
            }
        }
    }

    @Test
    void rateLimitMessageDoesNotEchoTheProviderBody() {
        server.enqueue(429, errorBody(429, "RESOURCE_EXHAUSTED"));

        assertThatThrownBy(() -> ask())
                .isInstanceOf(ChatAiException.class)
                .hasMessageNotContaining(PROVIDER_BODY);
    }
}
