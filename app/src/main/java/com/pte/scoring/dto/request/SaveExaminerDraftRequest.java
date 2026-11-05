package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record SaveExaminerDraftRequest(
        @NotNull UUID requestPublicId,
        long expectedWorkVersion,
        long expectedDraftVersion,
        @Min(0) @Max(100) Integer score,
        @Size(max = 2000) String comment,
        ExaminerDecisionKind decisionKind,
        String expectedReviewVersion,
        String reviewedAiVersion,
        @Size(max = com.pte.scoring.internal.constant.RubricConstants.MAX_TRAIT_SCORE_ENTRIES) java.util.Map<String, java.math.BigDecimal> traitScores
) {
    public SaveExaminerDraftRequest(UUID request, long work, long draft, Integer score, String comment,
            ExaminerDecisionKind kind, String review, String ai) {
        this(request, work, draft, score, comment, kind, review, ai, null);
    }
}
