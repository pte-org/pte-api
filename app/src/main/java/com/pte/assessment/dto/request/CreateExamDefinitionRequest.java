package com.pte.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Creates an empty editable DRAFT template. Items are added in the admin editor. {@code code} is optional: when
 * blank the service derives one from the name.
 */
public record CreateExamDefinitionRequest(
        @Size(max = 64) String code,
        @NotBlank @Size(max = 255) String name,
        String templatePolicy) {

    public CreateExamDefinitionRequest(String code, String name) {
        this(code, name, null);
    }
}
