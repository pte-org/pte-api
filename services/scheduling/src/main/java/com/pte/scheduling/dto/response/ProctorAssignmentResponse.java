package com.pte.scheduling.dto.response;

import com.pte.scheduling.domain.enums.ProctorRole;

import java.util.UUID;

public record ProctorAssignmentResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId, ProctorRole role) {
}
