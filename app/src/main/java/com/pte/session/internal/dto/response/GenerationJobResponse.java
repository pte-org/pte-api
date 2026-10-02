package com.pte.session.internal.dto.response;

import com.pte.session.domain.enums.GenerationJobStatus;

import java.util.UUID;

public record GenerationJobResponse(
        UUID publicId,
        UUID sessionPublicId,
        GenerationJobStatus status,
        int formsTotal,
        int formsCompleted,
        String algorithmVersion) {
}
