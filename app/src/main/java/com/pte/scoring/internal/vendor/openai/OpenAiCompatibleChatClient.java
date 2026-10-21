package com.pte.scoring.internal.vendor.openai;

import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.VendorHttp;
import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.domain.enums.AiProviderCategory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small OpenAI-compatible chat transport shared by text and audio adapters.
 * It accepts only the strict JSON score envelope and never supplies a fallback
 * score when the provider response is unusable. The HTTP call goes through a
 * {@link ProviderGuard}, like the Azure and Gemini clients, so transient
 * provider errors are retried within the job's budget.
 */
@Component
public class OpenAiCompatibleChatClient {

    static final String VENDOR = "OPENAI_COMPATIBLE";

    private final RestClient restClient;
    private final AiProviderProperties properties;
    private final JsonMapper jsonMapper;
    private final ProviderGuard guard;

    public OpenAiCompatibleChatClient(@Qualifier("aiProviderRestClient") RestClient restClient,
                                      AiProviderProperties properties, JsonMapper jsonMapper, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
        this.guard = ProviderGuard.standard(VENDOR, clock);
    }

    public AiScoreResult complete(String model, List<Map<String, Object>> messages) {
        if (model == null || model.isBlank()) {
            throw new AiProviderException(ScoringConstants.AI_MODEL_NOT_CONFIGURED);
        }
        if (messages == null || messages.isEmpty()) {
            throw new AiProviderException(ScoringConstants.AI_REQUEST_NO_MESSAGES);
        }

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("messages", messages);
        request.put("temperature", 0);
        request.put("max_tokens", properties.getMaxTokens());
        request.put("response_format", Map.of("type", "json_object"));

        String requestJson;
        try {
            requestJson = jsonMapper.writeValueAsString(request);
        } catch (RuntimeException ex) {
            throw new AiProviderException(ScoringConstants.AI_REQUEST_SERIALIZATION_FAILED, ex);
        }

        String responseJson = guard.call(() -> post(requestJson));
        if (responseJson == null || responseJson.isBlank()) {
            throw new AiProviderException(ScoringConstants.AI_EMPTY_RESPONSE);
        }
        return parseScore(responseJson, model);
    }

    private String post(String requestJson) {
        try {
            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            // The exception message quotes the provider body, so only the status and retry hint are kept.
            int status = ex.getStatusCode().value();
            HttpHeaders headers = ex.getResponseHeaders();
            throw VendorHttp.httpFailure(VENDOR, VendorHttp.classifyStatus(status), status,
                    VendorHttp.retryAfter(headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER)));
        } catch (RestClientException ex) {
            Throwable cause = ex.getMostSpecificCause();
            boolean timedOut = cause instanceof SocketTimeoutException || cause instanceof HttpTimeoutException;
            throw VendorHttp.failure(VENDOR,
                    timedOut ? AiFailureReason.PROVIDER_TIMEOUT : AiFailureReason.PROVIDER_NETWORK_ERROR);
        }
    }

    private AiScoreResult parseScore(String responseJson, String requestedModel) {
        try {
            JsonNode root = jsonMapper.readTree(responseJson);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw invalidResponse(ScoringConstants.AI_RESPONSE_CONTENT_NOT_TEXT, null);
            }
            JsonNode scoreEnvelope = jsonMapper.readTree(stripMarkdownFence(content.asText()));
            JsonNode rawScore = scoreEnvelope.path("rawScore");
            if (!rawScore.isIntegralNumber()) {
                throw invalidResponse(ScoringConstants.RAW_SCORE_NOT_INTEGER, null);
            }

            Map<String, Integer> subScores = parseSubScores(scoreEnvelope.path("subScores"));
            String feedback = scoreEnvelope.has("feedback")
                    ? scoreEnvelope.path("feedback").asText()
                    : "";
            try {
                String reportedModel = root.path("model").asText();
                if (reportedModel == null || reportedModel.isBlank()) {
                    reportedModel = requestedModel;
                }
                return new AiScoreResult(rawScore.asInt(), subScores, feedback,
                        AiProviderCategory.REAL, VENDOR, reportedModel, null);
            } catch (IllegalArgumentException ex) {
                throw invalidResponse(ex.getMessage(), ex);
            }
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidResponse(ScoringConstants.AI_RESPONSE_PARSE_FAILED, ex);
        }
    }

    private Map<String, Integer> parseSubScores(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw invalidResponse(ScoringConstants.SUBSCORES_NOT_OBJECT, null);
        }

        Map<String, Integer> subScores = new LinkedHashMap<>();
        node.propertyNames().forEach(name -> {
            JsonNode value = node.path(name);
            if (!value.isIntegralNumber()) {
                throw invalidResponse(ScoringConstants.SUBSCORES_MUST_CONTAIN_INTEGERS, null);
            }
            subScores.put(name, value.asInt());
        });
        return subScores;
    }

    private String stripMarkdownFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd < 0 || lastFence <= firstLineEnd) {
            throw invalidResponse(ScoringConstants.MALFORMED_JSON_MARKDOWN_FENCE, null);
        }
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private AiProviderException invalidResponse(String message, Throwable cause) {
        return cause == null
                ? new AiProviderException(message)
                : new AiProviderException(message, cause);
    }
}
