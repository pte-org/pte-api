package com.pte.assessment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A host names its new template; the items start as a copy of the platform default template. */
public record CreateHostExamTemplateRequest(@NotBlank @Size(max = 255) String name) {
}
