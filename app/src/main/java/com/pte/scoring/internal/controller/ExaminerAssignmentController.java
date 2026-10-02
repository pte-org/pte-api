package com.pte.scoring.internal.controller;

import com.pte.scoring.dto.request.CreateExaminerAssignmentPreviewRequest;
import com.pte.scoring.dto.response.ExaminerAssignmentOverviewResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentPreviewResponse;
import com.pte.scoring.internal.service.ExaminerAssignmentService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Host-only, per-session Examiner allocation preview and confirmation. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionPublicId}/examiner-assignments")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ExaminerAssignmentController {

    private final ExaminerAssignmentService assignmentService;

    public ExaminerAssignmentController(ExaminerAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ApiResponse<ExaminerAssignmentOverviewResponse> overview(@PathVariable UUID sessionPublicId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(assignmentService.overview(sessionPublicId, currentUser(), page, size));
    }

    @PostMapping("/previews")
    public ApiResponse<ExaminerAssignmentPreviewResponse> preview(@PathVariable UUID sessionPublicId,
            @Valid @RequestBody CreateExaminerAssignmentPreviewRequest request) {
        return ApiResponse.success(assignmentService.preview(sessionPublicId, request, currentUser()));
    }

    @PostMapping("/{batchPublicId}/confirm")
    public ApiResponse<ExaminerAssignmentPreviewResponse> confirm(@PathVariable UUID sessionPublicId,
            @PathVariable UUID batchPublicId) {
        return ApiResponse.success(assignmentService.confirm(sessionPublicId, batchPublicId, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
