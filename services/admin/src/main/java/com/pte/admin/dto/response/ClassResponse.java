package com.pte.admin.dto.response;

import java.util.UUID;

public record ClassResponse(
        UUID publicId,
        UUID programPublicId,
        String name,
        String status) {
}
