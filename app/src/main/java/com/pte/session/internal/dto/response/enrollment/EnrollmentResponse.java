package com.pte.session.internal.dto.response.enrollment;

import java.util.UUID;

public record EnrollmentResponse(UUID publicId, UUID sessionPublicId, UUID studentPublicId) {
}
