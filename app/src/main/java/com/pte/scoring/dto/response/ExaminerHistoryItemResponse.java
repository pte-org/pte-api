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
        Long draftVersion
) {}
