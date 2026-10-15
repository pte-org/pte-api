package com.pte.scoring.dto.request;

import com.pte.scoring.domain.enums.ExaminerGradingIssueType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ReportGradingIssueRequest(
        @NotNull UUID requestPublicId,
        long expectedWorkVersion,
        UUID answerPublicId,
        @NotNull ExaminerGradingIssueType type,
        @NotBlank @Size(max = 2000) String description
) {}
