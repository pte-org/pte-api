package com.pte.scheduling.controller;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
import com.pte.scheduling.dto.request.AssignProctorRequest;
import com.pte.scheduling.dto.request.UpdateProctorRoleRequest;
import com.pte.scheduling.dto.response.ProctorAssignmentResponse;
import com.pte.scheduling.service.EnrollmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    @GetMapping
    public ApiResponse<List<ProctorAssignmentResponse>> list(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(enrollmentService.listProctors(sessionPublicId, currentUser()));
    }

    @PatchMapping("/{assignmentPublicId}")
    public ApiResponse<ProctorAssignmentResponse> updateRole(@PathVariable UUID sessionPublicId,
                                                              @PathVariable UUID assignmentPublicId,
                                                              @Valid @RequestBody UpdateProctorRoleRequest request) {
        return ApiResponse.success(
                enrollmentService.updateProctorRole(sessionPublicId, assignmentPublicId, request, currentUser()));
    }

    @DeleteMapping("/{assignmentPublicId}")
    public ApiResponse<Void> unassign(@PathVariable UUID sessionPublicId, @PathVariable UUID assignmentPublicId) {
        enrollmentService.unassignProctor(sessionPublicId, assignmentPublicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
