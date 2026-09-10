package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.response.StudentEnrollmentResponse;
import com.pte.scheduling.service.EnrollmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A student's enrollment history across sessions, tenant-scoped to the
 * caller. Consumed by {@code admin}'s Class-transfer flow to surface a
 * pending-exam-request warning — read-only, never blocks the transfer.
 */
@RestController
@RequestMapping("/students/{studentPublicId}/enrollments")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class StudentEnrollmentController {

    private final EnrollmentService enrollmentService;

    public StudentEnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public ApiResponse<List<StudentEnrollmentResponse>> list(@PathVariable UUID studentPublicId) {
        return ApiResponse.success(enrollmentService.listForStudent(studentPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
