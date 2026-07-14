package com.aptis.modules.iam.dto.request.studentimport;

import com.aptis.modules.iam.domain.enums.UsernamePattern;

import jakarta.validation.constraints.NotNull;

public record UsernamePatternConfig(
        @NotNull UsernamePattern type,
        String sourceColumn) {
}
