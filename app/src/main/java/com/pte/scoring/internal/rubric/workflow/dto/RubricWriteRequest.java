package com.pte.scoring.internal.rubric.workflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

/** Full numeric parameters; strict codec/validator rejects every unknown key or change of trait scale. */
public record RubricWriteRequest(@NotNull JsonNode parameters, @Min(0) Long expectedLockVersion) { }
