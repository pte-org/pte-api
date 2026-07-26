package com.pte.scheduling.dto.response;

import java.util.UUID;

public record ProctorAssignmentResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId) {
}
