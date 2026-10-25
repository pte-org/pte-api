package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.dto.response.ClassResponse;

import java.util.UUID;

/**
 * Maps {@link TrainingClass} to its response DTO. Takes {@code programPublicId}
 * explicitly rather than reading it off {@code trainingClass.getProgram()} â€”
 * same reasoning as {@code ProgramMapper} â€” so a list loop never lazy-loads
 * the {@code program} association per row.
 */
public final class TrainingClassMapper {

    private TrainingClassMapper() {
    }

    public static ClassResponse toResponse(TrainingClass trainingClass, UUID programPublicId) {
        return new ClassResponse(
                trainingClass.getPublicId(),
                programPublicId,
                trainingClass.getName(),
                trainingClass.getStatus().name());
    }
}
