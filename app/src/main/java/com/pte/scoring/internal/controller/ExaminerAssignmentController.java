package com.pte.scoring.internal.controller;

import com.pte.scoring.dto.request.CommitExaminerAssignmentRequest;
import com.pte.scoring.dto.response.ExaminerAssignmentOverviewResponse;
import com.pte.scoring.dto.response.ExaminerAssignmentRosterResponse;
import com.pte.scoring.internal.service.examiner.ExaminerAssignmentService;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Host-only, per-session examiner assignment: read the roster, commit the full table once. */
@RestController
@RequestMapping("/api/v1/sessions/{sessionPublicId}/examiner-assignments")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class ExaminerAssignmentController {

    private final ExaminerAssignmentService assignmentService;

    public ExaminerAssignmentController(ExaminerAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public ApiResponse<ExaminerAssignmentOverviewResponse> overview(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(assignmentService.overview(sessionPublicId, currentUser()));
    }

    @GetMapping("/roster")
    public ApiResponse<ExaminerAssignmentRosterResponse> roster(@PathVariable UUID sessionPublicId) {
        return ApiResponse.success(assignmentService.roster(sessionPublicId, currentUser()));
    }

    @PostMapping("/commit")
    public ApiResponse<ExaminerAssignmentRosterResponse> commit(@PathVariable UUID sessionPublicId,
            @Valid @RequestBody CommitExaminerAssignmentRequest request) {
        return ApiResponse.success(assignmentService.commit(sessionPublicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
