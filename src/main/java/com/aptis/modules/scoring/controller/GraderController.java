package com.aptis.modules.scoring.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.aptis.common.response.ApiResponse;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.scoring.constant.ScoringApiConstants;
import com.aptis.modules.scoring.dto.request.GradeAnswerRequest;
import com.aptis.modules.scoring.dto.response.AnswerResponse;
import com.aptis.modules.scoring.dto.response.AttemptResponse;
import com.aptis.modules.scoring.interfaces.GraderScoringOperations;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping(ScoringApiConstants.GRADER_BASE)
public class GraderController {

    private final GraderScoringOperations graderService;

    public GraderController(GraderScoringOperations graderService) {
        this.graderService = graderService;
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_GRADER)
    @GetMapping(ScoringApiConstants.GRADER_PATH_ATTEMPTS)
    public ResponseEntity<ApiResponse<List<AttemptResponse>>> listAttempts(
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        List<AttemptResponse> attempts = graderService.listAttempts(principal.userId());
        return ResponseEntity.ok(ApiResponse.success(attempts, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_GRADER)
    @GetMapping(ScoringApiConstants.GRADER_PATH_ATTEMPT_ANSWERS)
    public ResponseEntity<ApiResponse<List<AnswerResponse>>> getAttemptAnswers(
            @PathVariable Long attemptId,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        List<AnswerResponse> answers = graderService.getAttemptAnswers(principal.userId(), attemptId);
        return ResponseEntity.ok(ApiResponse.success(answers, servletRequest.getRequestURI()));
    }

    @PreAuthorize(IamApiConstants.AUTHORITY_GRADER)
    @PatchMapping(ScoringApiConstants.GRADER_PATH_GRADE_ANSWER)
    public ResponseEntity<ApiResponse<Void>> gradeAnswer(
            @PathVariable Long attemptId,
            @PathVariable Long answerId,
            @Valid @RequestBody GradeAnswerRequest request,
            @AuthenticationPrincipal JwtPrincipal principal,
            HttpServletRequest servletRequest) {
        graderService.gradeAnswer(principal.userId(), attemptId, answerId, request);
        return ResponseEntity.ok(ApiResponse.success(null, servletRequest.getRequestURI()));
    }
}
