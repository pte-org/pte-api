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
        UUID requestPublicId) {
}
