package com.pte.admin.controller;

import com.pte.admin.dto.request.AssignLecturerRequest;
import com.pte.admin.dto.response.LecturerAssignmentResponse;
import com.pte.admin.service.AssignmentService;
import com.pte.common.security.CurrentUser;
import com.pte.common.security.CurrentUserContext;
import com.pte.common.web.ApiResponse;
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
 * Host self-service Lecturer assignment for a Class — list is available to
 * `HOST_ADMIN`/`HOST_AUTHOR`, assign/unassign are `HOST_ADMIN`-only (same
 * narrower-than-list pattern as {@code scheduling.ProctorAssignmentController}/
 * {@code EnrollmentController}).
 */
@RestController
@RequestMapping("/organizations/{organizationPublicId}/programs/{programPublicId}/classes/{classPublicId}/lecturers")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class LecturerAssignmentController {

    private final AssignmentService assignmentService;

    public LecturerAssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<LecturerAssignmentResponse> assign(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId,
            @Valid @RequestBody AssignLecturerRequest request) {
        return ApiResponse.success(
                assignmentService.assignLecturer(organizationPublicId, programPublicId, classPublicId, request,
                        currentUser()));
    }

    @GetMapping
    public ApiResponse<List<LecturerAssignmentResponse>> list(@PathVariable UUID organizationPublicId,
            @PathVariable UUID programPublicId, @PathVariable UUID classPublicId) {
        return ApiResponse.success(
                assignmentService.listLecturers(organizationPublicId, programPublicId, classPublicId, currentUser()));
    }

    @DeleteMapping("/{assignmentPublicId}")
    @PreAuthorize("hasRole('HOST_ADMIN')")
    public ApiResponse<Void> unassign(@PathVariable UUID organizationPublicId, @PathVariable UUID programPublicId,
            @PathVariable UUID classPublicId, @PathVariable UUID assignmentPublicId) {
        assignmentService.unassignLecturer(organizationPublicId, programPublicId, classPublicId, assignmentPublicId,
                currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.current()
                .orElseThrow(() -> new IllegalStateException("No authenticated principal"));
    }
}
