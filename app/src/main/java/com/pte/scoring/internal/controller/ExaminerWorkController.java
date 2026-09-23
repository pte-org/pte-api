package com.pte.scoring.internal.controller;

import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerQueueResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.internal.service.ExaminerScoringService;
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

/** Examiner-only work API. Every operation derives its owner from the authenticated principal. */
@RestController
@RequestMapping("/api/v1/examiner/work")
@PreAuthorize("hasRole('EXAMINER')")
public class ExaminerWorkController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerScoringService examinerScoringService;

    public ExaminerWorkController(ExaminerScoringService examinerScoringService) {
        this.examinerScoringService = examinerScoringService;
    }

    @GetMapping
    public ApiResponse<ExaminerQueueResponse> list(
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @RequestParam(required = false) UUID sessionPublicId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.success(examinerScoringService.listWork(status, sessionPublicId,
                page == null || page < 0 ? 0 : page,
                size == null || size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE), currentUser()));
    }

    @GetMapping("/{sessionPublicId}/{attemptPublicId}")
    public ApiResponse<ExaminerAttemptDetailResponse> getAttempt(@PathVariable UUID sessionPublicId,
            @PathVariable UUID attemptPublicId) {
        return ApiResponse.success(examinerScoringService.getAttempt(sessionPublicId, attemptPublicId, currentUser()));
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
