package com.pte.session.internal.dto.response;

import java.util.UUID;

public record SessionClassAssignmentResponse(UUID sessionPublicId, UUID classPublicId) {
}
