package com.pte.assessment.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** Sets the CEFR mix of a DRAFT template; the percents must be whole numbers summing to 100. */
public record UpdateCefrTargetsRequest(@NotEmpty @Valid List<Target> targets) {

    public record Target(@NotNull String level, @NotNull @Positive Integer percent) {
    }
}
