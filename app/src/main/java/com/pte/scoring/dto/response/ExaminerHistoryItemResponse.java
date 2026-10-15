package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import java.time.Instant;
import java.util.UUID;

public record ExaminerHistoryItemResponse(
        UUID eventPublicId,
        UUID sessionPublicId,
        UUID attemptPublicId,
        UUID answerPublicId,
        ExaminerGradingOperation operation,
        Instant occurredAt,
        long workVersion,
        Long draftVersion,
        UUID actorPublicId,
        String actorRole,
        UUID assignmentPublicId,
        Integer previousScore,
        Integer newScore,
        Long rubricVersion,
        String reason
) {
    public ExaminerHistoryItemResponse(
            UUID eventPublicId,
            UUID sessionPublicId,
            UUID attemptPublicId,
            UUID answerPublicId,
            ExaminerGradingOperation operation,
            Instant occurredAt,
            long workVersion,
            Long draftVersion) {
        this(eventPublicId, sessionPublicId, attemptPublicId, answerPublicId, operation, occurredAt, workVersion, draftVersion,
                null, null, null, null, null, null, null);
    }
}
