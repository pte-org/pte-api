package com.aptis.modules.examoperations.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateExamRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "code is required") String code,
        @NotNull(message = "settings is required") @Valid ExamSettingsRequest settings) {
    // hostId is deliberately NOT a client-supplied field — it is always resolved
    // server-side from the authenticated principal (see ExamService.createExam).
}
