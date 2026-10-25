package com.pte.session.internal.dto.response.proctor;

import com.pte.session.domain.enums.ProctorRole;

import java.util.UUID;

public record ProctorAssignmentResponse(UUID publicId, UUID sessionPublicId, UUID proctorPublicId, ProctorRole role) {
}
