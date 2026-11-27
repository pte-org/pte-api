package com.pte.session.internal.dto.response.proctor;

import java.util.UUID;

public record ProctorAssignmentResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId) {
}
