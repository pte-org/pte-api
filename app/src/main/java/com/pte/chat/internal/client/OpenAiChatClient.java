package com.pte.chat.internal.client;

import com.pte.chat.internal.constant.ChatConstants;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** OpenAI-compatible chat completions, one exchange with no automatic retry or retained student text. */
public final class OpenAiChatClient implements ChatAiClient {

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final Duration timeout;

    public OpenAiChatClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri, String apiKey,
                            String model, double temperature, int maxTokens, Duration timeout) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        // Preserve a configured /v1 or proxy prefix, as the question-generation gateway does.
        this.endpoint = URI.create(baseUri.toString().replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeout = timeout;
    }

    @Override
    public ChatAiAnswer generate(String systemInstruction, List<ChatTurn> history, String userMessage) {
        HttpResponse<byte[]> response = send(requestBody(systemInstruction, history, userMessage));
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new ChatAiException(classifyStatus(status), ChatConstants.PROVIDER_HTTP_STATUS_MESSAGE + status);
        }
        JsonNode root = readJson(response.body());
        JsonNode choice = root.path("choices").path(0);
        ChatAiUsage usage = usage(root, choice);
        requireUsableChoice(choice, usage);
        ChatModelOutput output;
        try {
            output = ChatOutputParser.parse(choice.path("message").path("content").asText(""));
        } catch (ChatAiException ex) {
            throw new ChatAiException(ex.reason(), ex.getMessage(), usage);
        }
        String servedModel = root.path("model").asText("");
        return new ChatAiAnswer(output.onTopic(), output.markdown(), usage, servedModel.isBlank() ? model : servedModel);
    }

    private Map<String, Object> requestBody(String systemInstruction, List<ChatTurn> history, String userMessage) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemInstruction));
        for (ChatTurn turn : history) {
            messages.add(Map.of("role", "model".equals(turn.role()) ? "assistant" : "user", "content", turn.text()));
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("response_format", Map.of("type", "json_object"));
        // Zero follows question generation: let the provider choose the budget for reasoning models.
        if (maxTokens > 0) {
            body.put("max_tokens", maxTokens);
        }
        return body;
    }

    private HttpResponse<byte[]> send(Map<String, Object> body) {
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(body)))
                .build();
        var call = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
        try {
            // Bound the complete body as well as the headers; never hold a student slot indefinitely.
            return call.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            call.cancel(true);
            throw new ChatAiException(ChatAiFailureReason.TIMEOUT, ChatConstants.PROVIDER_TIMEOUT_MESSAGE);
        } catch (ExecutionException ex) {
            ChatAiFailureReason reason = ex.getCause() instanceof HttpTimeoutException
                    ? ChatAiFailureReason.TIMEOUT : ChatAiFailureReason.NETWORK_ERROR;
            throw new ChatAiException(reason, ChatConstants.PROVIDER_UNREACHABLE_MESSAGE);
        } catch (InterruptedException ex) {
            call.cancel(true);
            Thread.currentThread().interrupt();
            throw new ChatAiException(ChatAiFailureReason.NETWORK_ERROR, ChatConstants.PROVIDER_INTERRUPTED_MESSAGE);
        }
    }

    private JsonNode readJson(byte[] body) {
        try {
            return jsonMapper.readTree(body);
        } catch (JacksonException ex) {
            // Jackson's cause may contain private response text.
            throw new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE, ChatConstants.PROVIDER_BODY_NOT_JSON_MESSAGE);
        }
    }

    private static ChatAiUsage usage(JsonNode root, JsonNode choice) {
        JsonNode metadata = root.path("usage");
        int completion = Math.max(0, metadata.path("completion_tokens").asInt(0));
        int thinking = Math.min(completion,
                Math.max(0, metadata.path("completion_tokens_details").path("reasoning_tokens").asInt(0)));
        return new ChatAiUsage(Math.max(0, metadata.path("prompt_tokens").asInt(0)), completion - thinking,
                thinking, choice.path("finish_reason").asText(null));
    }

    private static void requireUsableChoice(JsonNode choice, ChatAiUsage usage) {
        if (!choice.isObject()) {
            throw new ChatAiException(ChatAiFailureReason.EMPTY_RESPONSE,
                    ChatConstants.PROVIDER_NO_CANDIDATES_MESSAGE, usage);
        }
        String finish = usage.finishReason();
        if ("stop".equals(finish) && !choice.path("message").hasNonNull("refusal")) {
            return;
        }
        if ("content_filter".equals(finish) || choice.path("message").hasNonNull("refusal")) {
            throw new ChatAiException(ChatAiFailureReason.CONTENT_BLOCKED,
                    ChatConstants.PROVIDER_ANSWER_BLOCKED_MESSAGE, usage);
        }
        if ("length".equals(finish)) {
            throw new ChatAiException(ChatAiFailureReason.TRUNCATED, ChatConstants.PROVIDER_TOKEN_LIMIT_MESSAGE, usage);
        }
        throw new ChatAiException(ChatAiFailureReason.INVALID_RESPONSE,
                finish == null || finish.isBlank() ? ChatConstants.PROVIDER_NO_FINISH_REASON_MESSAGE
                        : ChatConstants.PROVIDER_UNEXPECTED_END_MESSAGE, usage);
    }

    private static ChatAiFailureReason classifyStatus(int status) {
        return switch (status) {
            case 401, 403 -> ChatAiFailureReason.AUTH_FAILED;
            case 408 -> ChatAiFailureReason.TIMEOUT;
            case 429 -> ChatAiFailureReason.RATE_LIMITED;
            default -> status >= 500 ? ChatAiFailureReason.UNAVAILABLE : ChatAiFailureReason.BAD_REQUEST;
        };
    }
}
