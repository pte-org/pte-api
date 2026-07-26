package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.request.AssignProctorRequest;
import com.pte.scheduling.dto.response.ProctorAssignmentResponse;
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
@RequestMapping("/sessions/{sessionPublicId}/proctors")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ProctorAssignmentController {

    private final EnrollmentService enrollmentService;

    public ProctorAssignmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @PostMapping
    public ApiResponse<ProctorAssignmentResponse> assign(@PathVariable UUID sessionPublicId,
                                                          @Valid @RequestBody AssignProctorRequest request) {
        return ApiResponse.success(enrollmentService.assignProctor(sessionPublicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
