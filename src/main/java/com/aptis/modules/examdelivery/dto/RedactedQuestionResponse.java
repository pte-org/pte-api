package com.aptis.modules.examdelivery.dto;

import java.util.List;
import java.util.UUID;

/**
 * Student-facing question content. Deliberately excludes correctAnswers, explanation, and
 * any other answer-key field — this is the single, mandatory shape for every endpoint that
 * returns Question content to a student (Phase 5 Design Constraint: no endpoint-by-endpoint
 * opt-out).
 */
public record RedactedQuestionResponse(
        UUID id,
        String skill,
        Integer part,
        String questionType,
        String content,
        String instruction,
        Integer timeLimit,
        Integer prepTime,
        Integer maxPlayCount,
        List<String> options,
        String audioUrl,
        Integer sequence) {
}
