package com.pte.scoring.dto.response;

import com.pte.scoring.domain.enums.ExaminerGradingIssueStatus;
import com.pte.scoring.domain.enums.ExaminerGradingIssueType;
import java.time.Instant;
import java.util.UUID;

public record ExaminerGradingIssueResponse(
        UUID issuePublicId,
        UUID sessionPublicId,
        UUID attemptPublicId,
        UUID answerPublicId,
        ExaminerGradingIssueType type,
        String description,
        ExaminerGradingIssueStatus status,
        Instant reportedAt,
        Instant resolvedAt,
        String resolution
) {}
