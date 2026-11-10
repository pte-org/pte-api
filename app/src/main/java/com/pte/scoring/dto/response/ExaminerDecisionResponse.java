package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;

import java.time.Instant;
import java.util.UUID;

/**
 * Authoritative human decision projection for an answer.
 * Includes viewed AI snapshot and the caller's own request public id.
 */
public record ExaminerDecisionResponse(
        String status,
        Integer score,
        ExaminerDecisionKind decisionKind,
        Instant finalizedAt,
        Integer reviewedAiScore,
        String reviewedAiVersion,
        UUID requestPublicId,
        java.util.Map<String, Integer> traitScores, UUID rubricVersionPublicId) {
    public ExaminerDecisionResponse(String status,
        Integer score,
        ExaminerDecisionKind decisionKind,
        Instant finalizedAt,
        Integer reviewedAiScore,
        String reviewedAiVersion,
        UUID requestPublicId) {
        this(status, score, decisionKind, finalizedAt, reviewedAiScore, reviewedAiVersion, requestPublicId, java.util.Map.of(), null);
    }

}
