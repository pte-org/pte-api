package com.pte.scheduling.dto.response;

import java.util.UUID;

public record EnrollmentResponse(UUID publicId, UUID sessionPublicId, UUID studentPublicId) {
}
