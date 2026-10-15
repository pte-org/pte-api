package com.pte.scoring.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record FinalizeExaminerWorkRequest(
        @NotNull UUID requestPublicId,
        long expectedWorkVersion,
        @NotNull List<@Valid FinalizeAnswerItemRequest> answers
) {
    public record FinalizeAnswerItemRequest(
            @NotNull UUID answerPublicId,
            long expectedDraftVersion,
            String expectedReviewVersion,
            String reviewedAiVersion
    ) {}
}
