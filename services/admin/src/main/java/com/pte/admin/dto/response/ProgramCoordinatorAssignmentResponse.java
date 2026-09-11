package com.pte.admin.dto.response;

import java.util.UUID;

public record ProgramCoordinatorAssignmentResponse(UUID publicId, UUID programPublicId, UUID assigneePublicId) {
}
