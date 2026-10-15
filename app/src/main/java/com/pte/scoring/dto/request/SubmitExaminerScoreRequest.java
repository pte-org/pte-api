package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitExaminerScoreRequest(
        @NotNull @Min(0) @Max(100) Integer score,
        ExaminerDecisionKind decisionKind,
        String expectedReviewVersion,
        String reviewedAiVersion,
        UUID requestPublicId) {

    public SubmitExaminerScoreRequest(Integer score) {
        this(score, null, null, null, null);
    }
}
