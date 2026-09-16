package com.pte.enrollment.internal.mapper;

import com.pte.enrollment.domain.ClassMembership;
import com.pte.enrollment.domain.Program;
import com.pte.enrollment.domain.StudentClass;
import com.pte.enrollment.internal.dto.response.ClassMembershipResponse;

/**
 * Maps {@link ClassMembership} to its response DTO, reading {@code className}/
 * {@code programName} off the loaded {@code studentClass}/{@code program}
 * associations. Safe to call both for a single freshly-loaded membership
 * (assign/unassign/transfer â€” one extra lazy load each is fine, it's not a
 * list) and for rows returned by {@code ClassMembershipRepository}'s
 * {@code @EntityGraph} queries (already join-fetched, so no lazy load at all).
 */
public final class ClassMembershipMapper {

    private ClassMembershipMapper() {
    }

    public static ClassMembershipResponse toResponse(ClassMembership membership) {
        StudentClass studentClass = membership.getStudentClass();
        Program program = studentClass.getProgram();
        return new ClassMembershipResponse(
                membership.getPublicId(),
                studentClass.getPublicId(),
                studentClass.getName(),
                program.getPublicId(),
                program.getName(),
                membership.getStudentPublicId());
    }
}
