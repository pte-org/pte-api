package com.pte.rubric.dto.request;

import jakarta.validation.constraints.Size;

public record PublishRubricRequest(@Size(max = 1000) String reason, Long expectedVersion) {
}
