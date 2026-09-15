package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.dto.response.ClassResponse;

import java.util.UUID;

/**
 * Maps {@link StudentClass} to its response DTO. Takes {@code programPublicId}
 * explicitly rather than reading it off {@code studentClass.getProgram()} â€”
 * same reasoning as {@code ProgramMapper} â€” so a list loop never lazy-loads
 * the {@code program} association per row.
 */
public final class StudentClassMapper {

    private StudentClassMapper() {
    }

    public static ClassResponse toResponse(StudentClass studentClass, UUID programPublicId) {
        return new ClassResponse(
                studentClass.getPublicId(),
                programPublicId,
                studentClass.getName(),
                studentClass.getStatus().name());
    }
}
