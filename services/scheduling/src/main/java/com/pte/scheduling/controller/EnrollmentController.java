package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.request.EnrollStudentRequest;
import com.pte.scheduling.dto.response.EnrollmentResponse;
import com.pte.scheduling.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/sessions/{sessionPublicId}/enrollments")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ApiResponse<EnrollmentResponse> enroll(@PathVariable UUID sessionPublicId,
                                                   @Valid @RequestBody EnrollStudentRequest request) {
        return ApiResponse.success(enrollmentService.enrollStudent(sessionPublicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
