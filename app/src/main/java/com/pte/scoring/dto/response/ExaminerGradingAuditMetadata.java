package com.pte.scoring.dto.response;

import java.util.UUID;

public record ExaminerGradingAuditMetadata(
        UUID actorPublicId,
        String actorRole,
        UUID assignmentPublicId,
        Integer previousScore,
        Integer newScore,
        Long rubricVersion,
        String reason
) {}
