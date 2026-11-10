package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitExaminerScoreRequest(
        @Min(0) @Max(100) Integer score,
        ExaminerDecisionKind decisionKind,
        String expectedReviewVersion,
        String reviewedAiVersion,
        UUID requestPublicId,
        @jakarta.validation.constraints.Size(max = com.pte.scoring.internal.constant.RubricConstants.MAX_TRAIT_SCORE_ENTRIES) java.util.Map<String, java.math.BigDecimal> traitScores) {

    public SubmitExaminerScoreRequest(Integer score, ExaminerDecisionKind kind, String review, String ai, UUID request) {
        this(score, kind, review, ai, request, null);
    }

    public SubmitExaminerScoreRequest(Integer score) {
        this(score, null, null, null, null);
    }
}
