package com.pte.scoring.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record StartExaminerWorkRequest(
        @NotNull UUID requestPublicId,
        long expectedWorkVersion
) {}
