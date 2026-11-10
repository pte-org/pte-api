package com.pte.scoring.dto.response;

import com.pte.scoring.rubric.ScoreTrace;

import java.util.UUID;

/**
 * The explanation of one answer's item score. {@code trace} is set only for {@link ItemTraceKind#RUBRIC};
 * {@code objectiveRuleCode} only for {@link ItemTraceKind#OBJECTIVE} task types the rubric catalog declares.
 */
public record ItemScoreTraceView(UUID answerPublicId, ItemTraceKind kind, String objectiveRuleCode,
                                 ScoreTrace trace) {
}
