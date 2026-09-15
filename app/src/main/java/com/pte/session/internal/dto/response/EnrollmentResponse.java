package com.pte.session.internal.dto.response;

import java.util.UUID;

public record EnrollmentResponse(UUID publicId, UUID sessionPublicId, UUID studentPublicId) {
}
