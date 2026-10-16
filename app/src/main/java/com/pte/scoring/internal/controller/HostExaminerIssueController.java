package com.pte.scoring.internal.controller;

import com.pte.scoring.dto.request.ResolveGradingIssueRequest;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.internal.service.ExaminerWorkflowCommandService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Host administration endpoints for resolving examiner grading issues. */
@RestController
@RequestMapping("/api/v1/host/examiner/issues")
@PreAuthorize("hasRole('HOST_ADMIN')")
public class HostExaminerIssueController {

    private final ExaminerWorkflowCommandService workflowCommandService;

    public HostExaminerIssueController(ExaminerWorkflowCommandService workflowCommandService) {
        this.workflowCommandService = workflowCommandService;
    }

    @PostMapping("/{issuePublicId}/resolve")
    public ApiResponse<ExaminerGradingIssueResponse> resolveIssue(
            @PathVariable UUID issuePublicId,
            @Valid @RequestBody ResolveGradingIssueRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(workflowCommandService.resolveIssue(issuePublicId, request, caller));
    }
}
