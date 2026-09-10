package com.pte.admin.dto.response;

import java.util.UUID;

public record LecturerAssignmentResponse(UUID publicId, UUID classPublicId, UUID assigneePublicId) {
}
