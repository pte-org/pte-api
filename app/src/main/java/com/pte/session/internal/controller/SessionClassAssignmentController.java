package com.pte.session.internal.controller;

import com.pte.session.internal.dto.request.AssignClassRequest;
import com.pte.session.internal.dto.response.SessionClassAssignmentResponse;
import com.pte.session.internal.service.SessionClassAssignmentService;
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

@RestController
@RequestMapping("/api/v1/sessions/{sessionPublicId}/classes")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class SessionClassAssignmentController {

    private final SessionClassAssignmentService assignmentService;

    public SessionClassAssignmentController(SessionClassAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @PostMapping
    public ApiResponse<SessionClassAssignmentResponse> assign(@PathVariable UUID sessionPublicId,
                                                               @Valid @RequestBody AssignClassRequest request) {
        return ApiResponse.success(assignmentService.assign(sessionPublicId, request.classPublicId(), currentUser()));
    }

    @GetMapping
    public ApiResponse<List<SessionClassAssignmentResponse>> list(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(assignmentService.list(sessionPublicId, currentUser()));
    }

    @DeleteMapping("/{classPublicId}")
    public ApiResponse<Void> unassign(@PathVariable UUID sessionPublicId, @PathVariable UUID classPublicId) {
        assignmentService.unassign(sessionPublicId, classPublicId, currentUser());
        return ApiResponse.success(null);
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
