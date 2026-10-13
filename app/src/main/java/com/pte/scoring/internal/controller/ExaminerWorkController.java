package com.pte.scoring.internal.controller;

import com.pte.scoring.dto.request.FinalizeExaminerWorkRequest;
import com.pte.scoring.dto.request.ReportGradingIssueRequest;
import com.pte.scoring.dto.request.SaveExaminerDraftRequest;
import com.pte.scoring.dto.request.StartExaminerWorkRequest;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerCommandResponse;
import com.pte.scoring.dto.response.ExaminerGradingIssueResponse;
import com.pte.scoring.dto.response.ExaminerGuidelinesResponse;
import com.pte.scoring.dto.response.ExaminerHistoryResponse;
import com.pte.scoring.dto.response.ExaminerQueueResponse;
import com.pte.scoring.dto.response.ExaminerReportsResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import com.pte.scoring.internal.service.ExaminerDraftCommandService;
import com.pte.scoring.internal.service.ExaminerScoringService;
import com.pte.scoring.internal.service.ExaminerWorkflowCommandService;
import com.pte.scoring.internal.service.ExaminerWorkflowQueryService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Examiner-only work API. Every operation derives its owner from the authenticated principal. */
@RestController
@RequestMapping("/api/v1/examiner/work")
@PreAuthorize("hasRole('EXAMINER')")
public class ExaminerWorkController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerScoringService examinerScoringService;
    private final ExaminerWorkflowCommandService workflowCommandService;
    private final ExaminerDraftCommandService draftCommandService;
    private final ExaminerWorkflowQueryService workflowQueryService;

    public ExaminerWorkController(
            ExaminerScoringService examinerScoringService,
            ExaminerWorkflowCommandService workflowCommandService,
            ExaminerDraftCommandService draftCommandService,
            ExaminerWorkflowQueryService workflowQueryService) {
        this.examinerScoringService = examinerScoringService;
        this.workflowCommandService = workflowCommandService;
        this.draftCommandService = draftCommandService;
        this.workflowQueryService = workflowQueryService;
    }

    @GetMapping("/overview")
    public ApiResponse<ExaminerWorkspaceOverviewResponse> getOverview() {
        return ApiResponse.success(examinerScoringService.getWorkspaceOverview(currentUser()));
    }

    @GetMapping("/reports")
    public ApiResponse<ExaminerReportsResponse> getReports(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false) String taskType,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) UUID sessionPublicId) {
        Instant fromInstant = parseInstant(fromDate);
        Instant toInstant = parseInstant(toDate);
        return ApiResponse.success(examinerScoringService.getReports(fromInstant, toInstant, taskType, status, sessionPublicId, currentUser()));
    }

    private static Instant parseInstant(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Instant.parse(text.trim());
        } catch (Exception e) {
            try {
                return java.time.LocalDate.parse(text.trim()).atStartOfDay().toInstant(java.time.ZoneOffset.UTC);
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    @GetMapping
    public ApiResponse<ExaminerQueueResponse> list(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) UUID sessionPublicId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(examinerScoringService.listWork(status, sessionPublicId, search, sort,
                page == null || page < 0 ? 0 : page,
                size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE), currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}")
    public ApiResponse<ExaminerAttemptDetailResponse> getAttempt(@PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId) {
        return ApiResponse.success(examinerScoringService.getAttempt(sessionPublicId, attemptPublicId, currentUser()));
    }

    @PostMapping("/{sessionPublicId}/{attemptPublicId}/start")
    public ApiResponse<ExaminerCommandResponse> startWork(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @Valid @RequestBody StartExaminerWorkRequest request) {
        return ApiResponse.success(workflowCommandService.start(sessionPublicId, attemptPublicId, request, currentUser()));
    }

    @PutMapping("/{sessionPublicId}/{attemptPublicId}/answers/{answerPublicId}/draft")
    public ApiResponse<ExaminerCommandResponse> saveDraft(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @PathVariable UUID answerPublicId,
            @Valid @RequestBody SaveExaminerDraftRequest request) {
        return ApiResponse.success(draftCommandService.saveDraft(sessionPublicId, attemptPublicId, answerPublicId, request, currentUser()));
    }

    @PostMapping("/{sessionPublicId}/{attemptPublicId}/finalize")
    public ApiResponse<ExaminerCommandResponse> finalizeWork(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @Valid @RequestBody FinalizeExaminerWorkRequest request) {
        return ApiResponse.success(draftCommandService.finalizeWork(sessionPublicId, attemptPublicId, request, currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}/commands/{requestPublicId}")
    public ApiResponse<ExaminerCommandResponse> getCommand(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @PathVariable UUID requestPublicId) {
        return ApiResponse.success(workflowCommandService.getCommand(sessionPublicId, attemptPublicId, requestPublicId, currentUser()));
    }

    @GetMapping("/history")
    public ApiResponse<ExaminerHistoryResponse> getHistory(
            @RequestParam UUID sessionPublicId,
            @RequestParam UUID attemptPublicId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int pageNum = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return ApiResponse.success(workflowQueryService.getHistory(sessionPublicId, attemptPublicId, PageRequest.of(pageNum, pageSize), currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}/history")
    public ApiResponse<ExaminerHistoryResponse> getAttemptHistory(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "20") Integer size) {
        int pageNum = page == null || page < 0 ? 0 : page;
        int pageSize = size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        return ApiResponse.success(workflowQueryService.getHistory(sessionPublicId, attemptPublicId, PageRequest.of(pageNum, pageSize), currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}/guidelines")
    public ApiResponse<ExaminerGuidelinesResponse> getGuidelines(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId) {
        return ApiResponse.success(workflowQueryService.getGuidelines(sessionPublicId, attemptPublicId, currentUser()));
    }

    @PostMapping("/{sessionPublicId}/{attemptPublicId}/issues")
    public ApiResponse<ExaminerCommandResponse> reportIssue(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId,
            @Valid @RequestBody ReportGradingIssueRequest request) {
        return ApiResponse.success(workflowCommandService.reportIssue(sessionPublicId, attemptPublicId, request, currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}/issues")
    public ApiResponse<List<ExaminerGradingIssueResponse>> getIssues(
            @PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId) {
        return ApiResponse.success(workflowQueryService.getIssues(sessionPublicId, attemptPublicId, currentUser()));
    }

    @PostMapping("/answers/{answerPublicId}/score")
    public ApiResponse<ExaminerScoreSubmissionResponse> submitScore(@PathVariable UUID answerPublicId,
            @Valid @RequestBody SubmitExaminerScoreRequest request) {
        return ApiResponse.success(examinerScoringService.submitScore(answerPublicId, request, currentUser()));
    }

    private CurrentUser currentUser() {
        return CurrentUserContext.required();
    }
}
