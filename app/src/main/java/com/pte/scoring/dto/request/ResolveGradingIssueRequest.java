package com.pte.scoring.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record ResolveGradingIssueRequest(
        @NotNull UUID requestPublicId,
        @NotBlank @Size(max = 2000) String resolution
) {}
