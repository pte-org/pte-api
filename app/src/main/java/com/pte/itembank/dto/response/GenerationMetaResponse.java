package com.pte.itembank.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * Public DTO: parsed generation_meta JSONB column.
 * Includes generator/reviewer models, prompt version, topic, review result, and metrics.
 * Exposed only to authoring callers; stripped for non-authoring contexts.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GenerationMetaResponse(
    int schema,
    String jobPublicId,
    Map<String, String> model,
    String promptVersion,
    String topic,
    int attempts,
    ReviewVerdictResponse review,
    GenerationMetricsResponse metrics,
    List<String> keyPoints,
    GenerationMediaResponse media,
    boolean editedByHuman) {

    public record GenerationMetricsResponse(
        int words,
        double avgSentenceLength,
        double fleschKincaid) {
    }

    public record GenerationMediaResponse(
        String voice,
        double speed,
        int durationSec) {
    }

    public record ReviewVerdictResponse(
        String verdict,
        Map<String, CheckResultResponse> checks,
        String fixSuggestion) {
    }

    public record CheckResultResponse(
        boolean ok,
        String note,
        String estimated) {
    }
}
