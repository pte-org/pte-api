package com.pte.enrollment.internal.dto.response;

import java.util.UUID;

public record ClassResponse(
        UUID publicId,
        UUID programPublicId,
        String name,
        String status) {
}
