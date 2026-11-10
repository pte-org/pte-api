package com.pte.reporting.internal.dto.response;

import com.pte.scoring.rubric.ScoreTrace;

import java.util.Map;
import java.util.UUID;

/**
 * One answer's item score and how it was reached. {@code scoreSource} is AI, EXAMINER or OBJECTIVE (null when there is
 * no score yet); {@code traceKind} names an {@code ItemTraceKind}; {@code trace} is the rubric computation (gate,
 * each trait's achieved/max/weight, unrounded percentage) and is set only for kind RUBRIC. {@code wordCount} is the
 * word count stored with the result (Read Aloud: the passage length N; Form-checked writing: the response length).
 */
public record ItemExplanationResponse(
        UUID answerPublicId,
        String taskType,
        String section,
        String scoreSource,
        Integer itemScore,
        String traceKind,
        Map<String, Integer> traitScores,
        Integer wordCount,
        UUID rubricVersionPublicId,
        String objectiveRuleCode,
        ScoreTrace trace) {
}
