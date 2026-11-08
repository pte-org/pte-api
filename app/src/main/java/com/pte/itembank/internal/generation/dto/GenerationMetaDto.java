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

    /**
     * What was made for the question. Audio has a voice, speed and length; a dialogue has no single speed and a chart
     * has neither, so every field may be null. The length is kept to one decimal.
     */
    public record GenerationMediaDto(
        String voice,
        Double speed,
        Double durationSec,
        String chartType) {
    }
}
