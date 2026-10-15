package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import java.time.Instant;
import java.util.UUID;

public record ExaminerDraftResponse(
        UUID answerPublicId,
        long draftVersion,
        Integer score,
        String comment,
        ExaminerDecisionKind decisionKind,
        String expectedReviewVersion,
        String reviewedAiVersion,
        Instant savedAt
) {}
