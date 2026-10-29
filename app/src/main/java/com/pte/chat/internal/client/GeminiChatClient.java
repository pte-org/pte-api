package com.pte.chat.internal.client;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Gemini {@code generateContent} for the student chat: a multi-turn request whose answer is forced into
 * {@code {is_on_topic, markdown_content}}. One call is one HTTP exchange; there is deliberately no retry,
 * a failed call is refunded by the caller and the student asks again. The API key travels only in the
 * {@code x-goog-api-key} header. Thinking is left at the model's default, so {@code ChatGeminiConfig} only
 * accepts a model whose default is off: thinking tokens count against {@code maxOutputTokens}.
 *
 * <p>Request fields checked against ai.google.dev on 2026-10-28: {@code generationConfig} with
 * {@code temperature}, {@code maxOutputTokens}, {@code responseMimeType}, {@code responseSchema} (uppercase
 * OpenAPI-subset types and {@code propertyOrdering}, the same shape the scoring client already runs in
 * production) and the response's {@code usageMetadata.thoughtsTokenCount}. The thinking-level field is not
 * sent because its REST name could not be verified. A {@code generateContent} answer without a
 * {@code finishReason} is rejected: only STOP is a normal end.
 */
public class GeminiChatClient {

    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION");
    private static final String FINISH_STOP = "STOP";
    private static final String FINISH_MAX_TOKENS = "MAX_TOKENS";
    // The response schema is read by the model in this order, so is_on_topic is decided before the answer.
    private static final List<String> SCHEMA_FIELDS = List.of("is_on_topic", "markdown_content");

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxOutputTokens;
    private final Duration timeout;

    public GeminiChatClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri, String apiKey,
                            String model, double temperature, int maxOutputTokens, Duration timeout) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.endpoint = baseUri.resolve("/v1beta/models/" + model + ":generateContent");
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxOutputTokens = maxOutputTokens;
        this.timeout = timeout;
    }

    public ChatAiAnswer generate(String systemInstruction, List<ChatTurn> history, String userMessage) {
        HttpResponse<byte[]> response = send(requestBody(systemInstruction, history, userMessage));
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new ChatAiException(classifyStatus(status, response.body()), "Chat provider answered HTTP " + status);
        }
        JsonNode root = readJson(response.body());
        JsonNode candidate = root.path("candidates").path(0);
        ChatAiUsage usage = usage(root, candidate);
        requireUsableCandidate(root, candidate, usage);
        ChatModelOutput output;
        try {
            output = ChatOutputParser.parse(answerText(candidate));
        } catch (ChatAiException ex) {
            // Keep the real token cost: the call was paid for even though the text is unusable.
            throw new ChatAiException(ex.reason(), ex.getMessage(), usage);
        }
        String servedModel = root.path("modelVersion").asText("");
        return new ChatAiAnswer(output.onTopic(), output.markdown(), usage, servedModel.isBlank() ? model : servedModel);
    }

    private Map<String, Object> requestBody(String systemInstruction, List<ChatTurn> history, String userMessage) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : history) {
            contents.add(turn(turn.role(), turn.text()));
        }
        contents.add(turn("user", userMessage));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", temperature,
                "maxOutputTokens", maxOutputTokens,
                "responseMimeType", "application/json",
                "responseSchema", responseSchema()));
        return body;
    }

    private static Map<String, Object> turn(String role, String text) {
        return Map.of("role", role, "parts", List.of(Map.of("text", text)));
    }

    /** Kept to two fields on purpose: large schemas are rejected with INVALID_ARGUMENT. */
    private static Map<String, Object> responseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("is_on_topic", Map.of("type", "BOOLEAN"));
        properties.put("markdown_content", Map.of("type", "STRING"));
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "OBJECT");
        schema.put("properties", properties);
        schema.put("required", SCHEMA_FIELDS);
        schema.put("propertyOrdering", SCHEMA_FIELDS);
        return schema;
    }

    /**
     * The request timeout only bounds the wait for the response headers, so the call is also waited on
     * with it: a body that stalls after the headers times out instead of holding the request thread.
     */
    private HttpResponse<byte[]> send(Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(body)))
                .build();
        CompletableFuture<HttpResponse<byte[]>> call =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            call.cancel(true);
            throw new ChatAiException(ChatAiFailureReason.TIMEOUT, "Chat provider timed out", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            ChatAiFailureReason reason = cause instanceof HttpTimeoutException
                    ? ChatAiFailureReason.TIMEOUT
                    : ChatAiFailureReason.NETWORK_ERROR;
            throw new ChatAiException(reason, "Chat provider could not be reached", cause);
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new ChatAiException(ChatAiFailureReason.NETWORK_ERROR, "Chat provider call was interrupted", ex);
        }
    }

    private JsonNode readJson(byte[] body) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException ex) {
            // The parser message quotes the body, so the cause is dropped on purpose.
            throw new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE, "Chat provider body is not JSON");
        }
    }

    private static ChatAiUsage usage(JsonNode root, JsonNode candidate) {
        JsonNode metadata = root.path("usageMetadata");
        return new ChatAiUsage(
                metadata.path("promptTokenCount").asInt(0),
                metadata.path("candidatesTokenCount").asInt(0),
                metadata.path("thoughtsTokenCount").asInt(0),
                candidate.path("finishReason").asText(null));
    }

    private static void requireUsableCandidate(JsonNode root, JsonNode candidate, ChatAiUsage usage) {
        if (root.path("promptFeedback").hasNonNull("blockReason")) {
            throw new ChatAiException(ChatAiFailureReason.CONTENT_BLOCKED, "Chat prompt was blocked", usage);
        }
        if (!candidate.isObject()) {
            throw new ChatAiException(ChatAiFailureReason.EMPTY_RESPONSE, "Chat provider returned no candidates", usage);
        }
        String finish = usage.finishReason();
        if (FINISH_STOP.equals(finish)) {
            return;
        }
        if (finish == null || finish.isBlank()) {
            throw new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE, "Chat answer has no finish reason", usage);
        }
        if (BLOCKED_FINISH_REASONS.contains(finish)) {
            throw new ChatAiException(ChatAiFailureReason.CONTENT_BLOCKED, "Chat answer was blocked", usage);
        }
        if (FINISH_MAX_TOKENS.equals(finish)) {
            throw new ChatAiException(ChatAiFailureReason.TRUNCATED, "Chat answer hit the token limit", usage);
        }
        throw new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE, "Chat answer ended unexpectedly", usage);
    }

    /** Thought parts are the model's reasoning, not part of the answer. */
    private static String answerText(JsonNode candidate) {
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        return text.toString();
    }

    private ChatAiFailureReason classifyStatus(int status, byte[] body) {
        if (status == 429) {
            return ChatAiFailureReason.RATE_LIMITED;
        }
        if (status == 408) {
            return ChatAiFailureReason.TIMEOUT;
        }
        if (status >= 500) {
            return ChatAiFailureReason.UNAVAILABLE;
        }
        if (status == 401 || status == 403 || isInvalidKey(body)) {
            return ChatAiFailureReason.AUTH_FAILED;
        }
        return ChatAiFailureReason.BAD_REQUEST;
    }

    /** Google answers a bad key with 400 and a reason in the error details, not with 401. */
    private boolean isInvalidKey(byte[] body) {
        try {
            for (JsonNode detail : jsonMapper.readTree(body).path("error").path("details")) {
                if ("API_KEY_INVALID".equals(detail.path("reason").asText(""))) {
                    return true;
                }
            }
        } catch (JacksonException ex) {
            // Not JSON: a plain bad request.
        }
        return false;
    }
}
