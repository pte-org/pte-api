package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerGradingOperation;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ExaminerCommandReceiptResponse(
        UUID requestPublicId,
        ExaminerGradingOperation operation,
        Instant committedAt,
        long workVersion,
        UUID answerPublicId,
        Long draftVersion,
        List<ChildRequestReceipt> childRequests
) {
    public record ChildRequestReceipt(
            UUID answerPublicId,
            UUID requestPublicId
    ) {}
}
