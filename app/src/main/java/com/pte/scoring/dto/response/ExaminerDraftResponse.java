package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import java.time.Instant;
import java.util.UUID;

/** {@code carriedOver} marks a draft handed over by the previous examiner, which must be saved again before finalizing. */
public record ExaminerDraftResponse(
        UUID answerPublicId,
        long draftVersion,
        Integer score,
        String comment,
        ExaminerDecisionKind decisionKind,
        String expectedReviewVersion,
        String reviewedAiVersion,
        Instant savedAt,
        boolean carriedOver
) {}
