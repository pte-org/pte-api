package com.pte.admin.mapper;

import com.pte.admin.domain.Program;
import com.pte.admin.dto.response.ProgramResponse;

import java.util.UUID;

/**
 * Maps {@link Program} to its response DTO. Takes {@code organizationPublicId}
 * explicitly rather than reading it off {@code program.getOrganization()} —
 * callers already know it (from the request path or the just-saved parent),
 * so this avoids ever lazy-loading the {@code organization} association per row.
 */
public final class ProgramMapper {

    private ProgramMapper() {
    }

    public static ProgramResponse toResponse(Program program, UUID organizationPublicId) {
        return new ProgramResponse(
                program.getPublicId(),
                organizationPublicId,
                program.getName(),
                program.getDescription(),
                program.getStartDate(),
                program.getEndDate(),
                program.getStatus().name());
    }
}
