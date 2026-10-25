package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.ClassStudent;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.TrainingClass;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;

/**
 * Maps {@link ClassStudent} to its response DTO, reading {@code className}/
 * {@code programName} off the loaded {@code trainingClass}/{@code program}
 * associations. Safe to call both for a single freshly-loaded membership
 * (assign/unassign/transfer â€” one extra lazy load each is fine, it's not a
 * list) and for rows returned by {@code ClassStudentRepository}'s
 * {@code @EntityGraph} queries (already join-fetched, so no lazy load at all).
 */
public final class ClassStudentMapper {

    private ClassStudentMapper() {
    }

    public static ClassMembershipResponse toResponse(ClassStudent membership) {
        TrainingClass trainingClass = membership.getTrainingClass();
        Program program = trainingClass.getProgram();
        return new ClassMembershipResponse(
                membership.getPublicId(),
                trainingClass.getPublicId(),
                trainingClass.getName(),
                program.getPublicId(),
                program.getName(),
                membership.getStudentPublicId());
    }
}
