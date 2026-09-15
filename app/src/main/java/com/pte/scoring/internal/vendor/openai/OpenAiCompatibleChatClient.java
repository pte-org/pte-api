package com.pte.scoring.internal.vendor.openai;

import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.AiScoreResult;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Small OpenAI-compatible chat transport shared by text and audio adapters.
 * It accepts only the strict JSON score envelope and never supplies a fallback
 * score when the provider response is unusable.
 */
@Component
public class OpenAiCompatibleChatClient {

    private final RestClient restClient;
    private final AiProviderProperties properties;
    private final JsonMapper jsonMapper;

    public OpenAiCompatibleChatClient(@Qualifier("aiProviderRestClient") RestClient restClient,
                                      AiProviderProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public AiScoreResult complete(String model, List<Map<String, Object>> messages) {
        if (model == null || model.isBlank()) {
            throw new AiProviderException("AI model is not configured");
        }
        if (messages == null || messages.isEmpty()) {
            throw new AiProviderException("AI request has no messages");
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
            throw new AiProviderException("Could not serialize AI request", ex);
        }

        String responseJson;
        try {
            responseJson = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestJson)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new AiProviderException("AI provider request failed", ex);
        }
        if (responseJson == null || responseJson.isBlank()) {
            throw new AiProviderException("AI provider returned an empty response");
        }
        return parseScore(responseJson);
    }

    private AiScoreResult parseScore(String responseJson) {
        try {
            JsonNode root = jsonMapper.readTree(responseJson);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual()) {
                throw invalidResponse("choices[0].message.content is not text", null);
            }
            JsonNode scoreEnvelope = jsonMapper.readTree(stripMarkdownFence(content.asText()));
            JsonNode rawScore = scoreEnvelope.path("rawScore");
            if (!rawScore.isIntegralNumber()) {
                throw invalidResponse("rawScore must be an integer", null);
            }

            Map<String, Integer> subScores = parseSubScores(scoreEnvelope.path("subScores"));
            String feedback = scoreEnvelope.has("feedback")
                    ? scoreEnvelope.path("feedback").asText()
                    : "";
            try {
                return new AiScoreResult(rawScore.asInt(), subScores, feedback);
            } catch (IllegalArgumentException ex) {
                throw invalidResponse(ex.getMessage(), ex);
            }
        } catch (AiProviderException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw invalidResponse("Could not parse AI provider response", ex);
        }
    }

    private Map<String, Integer> parseSubScores(JsonNode node) {
        if (node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        if (!node.isObject()) {
            throw invalidResponse("subScores must be an object", null);
        }

        Map<String, Integer> subScores = new LinkedHashMap<>();
        node.propertyNames().forEach(name -> {
            JsonNode value = node.path(name);
            if (!value.isIntegralNumber()) {
                throw invalidResponse("subScores must contain integers", null);
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
            throw invalidResponse("Malformed JSON markdown fence", null);
        }
        return trimmed.substring(firstLineEnd + 1, lastFence).trim();
    }

    private AiProviderException invalidResponse(String message, Throwable cause) {
        return cause == null
                ? new AiProviderException(message)
                : new AiProviderException(message, cause);
    }
}
