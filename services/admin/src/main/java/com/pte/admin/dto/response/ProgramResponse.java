package com.pte.admin.dto.response;

import java.time.LocalDate;
import java.util.UUID;

public record ProgramResponse(
        UUID publicId,
        UUID organizationPublicId,
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        String status) {
}
