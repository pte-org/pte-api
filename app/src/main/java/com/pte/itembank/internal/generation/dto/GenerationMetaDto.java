package com.pte.itembank.internal.generation.dto;

import com.pte.itembank.internal.generation.review.ReviewVerdict;
import java.util.List;
import java.util.Map;

/**
 * Schema v1 for generation_meta JSONB column. Parsed from Question.generationMeta.
 * Includes generator/reviewer models, prompt version, topic, review result, metrics, and optional media info.
 */
public record GenerationMetaDto(
    int schema,
    String jobPublicId,
    Map<String, String> model,
    String promptVersion,
    String topic,
    int attempts,
    ReviewVerdict review,
    GenerationMetricsDto metrics,
    List<String> keyPoints,
    GenerationMediaDto media) {

    public record GenerationMetricsDto(
        int words,
        double avgSentenceLength,
        double fleschKincaid) {
    }

    public record GenerationMediaDto(
        String voice,
        double speed,
        int durationSec) {
    }
}
