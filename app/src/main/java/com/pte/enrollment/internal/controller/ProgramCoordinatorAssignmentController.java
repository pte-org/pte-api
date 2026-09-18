package com.pte.enrollment.internal.controller;

import com.pte.enrollment.internal.dto.request.AssignCoordinatorRequest;
import com.pte.enrollment.internal.dto.response.ProgramCoordinatorAssignmentResponse;
import com.pte.enrollment.internal.service.AssignmentService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Host self-service Program Coordinator assignment â€” list is available to
 * `HOST_ADMIN`, assign/unassign are `HOST_ADMIN`-only (same
 * narrower-than-list pattern as {@link LecturerAssignmentController}).
 */
@RestController
@RequestMapping("/api/v1/organizations/{organizationPublicId}/programs/{programPublicId}/coordinators")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ProgramCoordinatorAssignmentController {

    private final AssignmentService assignmentService;

    public ProgramCoordinatorAssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<ProgramCoordinatorAssignmentResponse> assign(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @Valid @RequestBody AssignCoordinatorRequest request) {
        return ApiResponse.success(
                assignmentService.assignCoordinator(organizationPublicId, programPublicId, request, currentUser()));
    }

    @GetMapping
    public ApiResponse<List<ProgramCoordinatorAssignmentResponse>> list(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId) {
        return ApiResponse.success(
                assignmentService.listCoordinators(organizationPublicId, programPublicId, currentUser()));
    }

    @DeleteMapping("/{assignmentPublicId}")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<Void> unassign(@PathVariable UUID organizationPublicId, @PathVariable UUID programPublicId,
            @PathVariable UUID assignmentPublicId) {
        assignmentService.unassignCoordinator(organizationPublicId, programPublicId, assignmentPublicId,
                currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
