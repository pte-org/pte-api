package com.pte.enrollment.internal.dto.response;

import com.pte.enrollment.dto.response.StudentAssignmentView;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Account and current assignment context for the tenant student workspace. */
public record StudentDetailResponse(
        UUID publicId,
        String username,
        String email,
        String fullName,
        UUID tenantId,
        String status,
        List<String> roles,
        String studentCode,
        String phone,
        LocalDate dateOfBirth,
        boolean mustChangePassword,
        StudentAssignmentView assignment) {
}
