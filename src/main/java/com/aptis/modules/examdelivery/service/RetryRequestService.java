package com.aptis.modules.examdelivery.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;
import com.aptis.modules.examdelivery.dto.RetryRequestResponse;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examdelivery.repository.RetryRequestRepository;
import com.aptis.modules.examoperations.domain.Exam;
import com.aptis.modules.examoperations.repository.ExamRepository;
import com.aptis.modules.iam.repository.GraderOrgAssignmentRepository;

/**
 * Standalone approval flow (Design Constraint): independent of any scoring-confirmation
 * queue. Every retry needs an explicit Host/Grader decision, on top of the exam's
 * {@code maxRetryCount} budget.
 */
@Service
public class RetryRequestService {

    private static final List<RetryRequestStatus> BLOCKING_STATUSES =
            List.of(RetryRequestStatus.PENDING, RetryRequestStatus.APPROVED);

    private final RetryRequestRepository retryRequestRepository;
    private final ExamAttemptRepository examAttemptRepository;
    private final ExamRepository examRepository;
    private final GraderOrgAssignmentRepository graderOrgAssignmentRepository;
    private final ExamAttemptAccessGuard accessGuard;

    public RetryRequestService(
            RetryRequestRepository retryRequestRepository,
            ExamAttemptRepository examAttemptRepository,
            ExamRepository examRepository,
            GraderOrgAssignmentRepository graderOrgAssignmentRepository,
            ExamAttemptAccessGuard accessGuard) {
        this.retryRequestRepository = retryRequestRepository;
        this.examAttemptRepository = examAttemptRepository;
        this.examRepository = examRepository;
        this.graderOrgAssignmentRepository = graderOrgAssignmentRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional
    public RetryRequestResponse requestRetry(Long attemptId, JwtPrincipal studentPrincipal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, studentPrincipal);

        if (!Boolean.TRUE.equals(attempt.getIsSubmitted())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, ExamDeliveryConstants.RETRY_ATTEMPT_NOT_SUBMITTED);
        }

        String studentId = attempt.getStudentId();
        Long examId = attempt.getExamId();

        if (retryRequestRepository.existsByStudentIdAndExamIdAndStatusIn(studentId, examId, BLOCKING_STATUSES)) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, ExamDeliveryConstants.RETRY_ALREADY_PENDING_OR_APPROVED);
        }

        Exam exam = examRepository.findById(examId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND));
        // max_retry_count is the TOTAL attempts allowed for this exam (Design Constraint,
        // Step 1) — e.g. 1 means exactly 1 attempt (no retries), 2 means 1 initial + 1 retry.
        long attemptsSoFar = examAttemptRepository.countByStudentIdAndExamId(studentId, examId);
        if (attemptsSoFar >= exam.getMaxRetryCount()) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, ExamDeliveryConstants.RETRY_BUDGET_EXHAUSTED);
        }

        RetryRequest request = RetryRequest.create(studentId, attemptId, examId);
        return toResponse(retryRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<RetryRequestResponse> listPending(JwtPrincipal reviewerPrincipal) {
        return retryRequestRepository.findByStatus(RetryRequestStatus.PENDING).stream()
                .filter(request -> canReview(request, reviewerPrincipal))
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RetryRequestResponse approve(Long requestId, JwtPrincipal reviewerPrincipal) {
        RetryRequest request = loadReviewableRequest(requestId, reviewerPrincipal);
        request.approve(reviewerPrincipal.userId());
        return toResponse(retryRequestRepository.save(request));
    }

    @Transactional
    public RetryRequestResponse reject(Long requestId, JwtPrincipal reviewerPrincipal) {
        RetryRequest request = loadReviewableRequest(requestId, reviewerPrincipal);
        request.reject(reviewerPrincipal.userId());
        return toResponse(retryRequestRepository.save(request));
    }

    // ── Private helpers ──────────────────────────────────────────────

    private RetryRequest loadReviewableRequest(Long requestId, JwtPrincipal reviewerPrincipal) {
        RetryRequest request = retryRequestRepository.findById(requestId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.RETRY_REQUEST_NOT_FOUND + requestId));

        // Tenant check before role-specific branching (Design Constraint): a 404, not 403,
        // so an out-of-tenant reviewer cannot confirm the request even exists.
        if (!canReview(request, reviewerPrincipal)) {
            throw new ApiException(
                    ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.RETRY_REQUEST_NOT_FOUND + requestId);
        }
        return request;
    }

    private boolean canReview(RetryRequest request, JwtPrincipal reviewerPrincipal) {
        Exam exam = examRepository.findById(request.getExamId()).orElse(null);
        if (exam == null) {
            return false;
        }
        if ("HOST".equals(reviewerPrincipal.userType())) {
            return exam.getHostId().equals(reviewerPrincipal.userId().toString());
        }
        if ("GRADER".equals(reviewerPrincipal.userType())) {
            return exam.getOrganizationId() != null
                    && graderOrgAssignmentRepository.existsByIdGraderIdAndIdOrganizationId(
                            reviewerPrincipal.userId(), exam.getOrganizationId());
        }
        return false;
    }

    private RetryRequestResponse toResponse(RetryRequest request) {
        return new RetryRequestResponse(
                request.getId(),
                request.getStudentId(),
                request.getAttemptId(),
                request.getExamId(),
                request.getStatus().name(),
                request.getRequestedAt(),
                request.getReviewedAt(),
                request.getReviewedBy());
    }
}
