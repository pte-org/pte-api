package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat transport for the 9router. It logs only the role, model, duration, token usage and an
 * outcome code: never the prompt, the response or the key. A transient failure (429, 5xx, timeout) is retried
 * once; auth failures and other 4xx are not, redirects are never followed.
 */
public class NineRouterLlmGateway implements LlmGateway {

    private static final Logger LOG = LoggerFactory.getLogger(NineRouterLlmGateway.class);
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final int MAX_ATTEMPTS = 2;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String FENCE = "```";
    private static final String NEWLINE = "\n";

    private final RestClient restClient;
    private final QuestionGenerationProperties.Llm llm;
    private final JsonMapper jsonMapper;

    public NineRouterLlmGateway(RestClient restClient, QuestionGenerationProperties properties,
                                JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.llm = properties.getLlm();
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String completeJson(LlmRole role, String systemPrompt, String userPrompt) {
        String model = role == LlmRole.GENERATOR ? llm.getGeneratorModel() : llm.getReviewerModel();
        double temperature = role == LlmRole.GENERATOR ? llm.getGeneratorTemperature()
                : llm.getReviewerTemperature();
        String body = requestBody(model, temperature, systemPrompt, userPrompt);
        long started = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                return post(body, role, model, started);
            } catch (LlmProviderException failure) {
                boolean retry = (failure.getType() == LlmFailureType.UNAVAILABLE
                        || failure.getType() == LlmFailureType.EMPTY_RESPONSE) && attempt < MAX_ATTEMPTS;
                LOG.warn("Generation provider call failed: role={}, model={}, code={}, attempt={}, retry={}",
                        role, model, failure.getMessage(), attempt, retry);
                if (!retry) {
                    throw failure;
                }
                pause();
            }
        }
    }

    private String requestBody(String model, double temperature, String systemPrompt, String userPrompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", temperature);
        // 0 = no cap: reasoning models spend their token allowance on thinking and can return an empty answer.
        if (llm.getMaxTokens() > 0) {
            request.put("max_tokens", llm.getMaxTokens());
        }
        // The 9router combo models stream (text/event-stream) unless told otherwise; the client reads one JSON body.
        request.put("stream", false);
        request.put("response_format", Map.of("type", "json_object"));
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));
        return jsonMapper.writeValueAsString(request);
    }

    private String post(String body, LlmRole role, String model, long started) {
        String response;
        try {
            response = restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, reply) -> {
                        throw new LlmProviderException(LlmFailureType.REJECTED);
                    })
                    .body(String.class);
        } catch (RestClientResponseException failure) {
            // The exception message quotes the provider body, so only the status code is used.
            throw new LlmProviderException(classify(failure.getStatusCode().value()));
        } catch (RestClientException failure) {
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
        String content = extractContent(response);
        LOG.info("Generation provider call ok: role={}, model={}, durationMs={}, totalTokens={}", role, model,
                (System.nanoTime() - started) / NANOS_PER_MILLI, totalTokens(response));
        return content;
    }

    private String extractContent(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        JsonNode content;
        try {
            content = jsonMapper.readTree(response).path("choices").path(0).path("message").path("content");
        } catch (RuntimeException unparsable) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        return stripFence(content.asText().trim());
    }

    private long totalTokens(String response) {
        try {
            return jsonMapper.readTree(response).path("usage").path("total_tokens").asLong(-1);
        } catch (RuntimeException unparsable) {
            return -1;
        }
    }

    private static String stripFence(String text) {
        if (!text.startsWith(FENCE)) {
            return text;
        }
        int firstLineEnd = text.indexOf(NEWLINE);
        int closing = text.lastIndexOf(FENCE);
        if (firstLineEnd < 0 || closing <= firstLineEnd) {
            return text;
        }
        return text.substring(firstLineEnd + 1, closing).trim();
    }

    private static LlmFailureType classify(int status) {
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            return LlmFailureType.AUTH;
        }
        if (status == TOO_MANY_REQUESTS || status >= SERVER_ERROR) {
            return LlmFailureType.UNAVAILABLE;
        }
        return LlmFailureType.REJECTED;
    }

    private void pause() {
        try {
            Thread.sleep(llm.getRetryDelay().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
    }
}
