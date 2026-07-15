package com.aptis.modules.examdelivery.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.examoperations.domain.Exam;

/**
 * Shared ownership + availability-window enforcement for every student-facing exam-attempt
 * endpoint (content delivery and answer submission alike). Centralized here because the
 * pre-existing controller had neither check on any endpoint — every attempt-scoped action
 * must go through this guard, not re-implement the check inline.
 */
@Component
public class ExamAttemptAccessGuard {

    private final ExamAttemptRepository examAttemptRepository;

    public ExamAttemptAccessGuard(ExamAttemptRepository examAttemptRepository) {
        this.examAttemptRepository = examAttemptRepository;
    }

    /** Loads the attempt and verifies the authenticated student owns it (404, not 403, to avoid confirming existence). */
    public ExamAttempt loadOwnedAttempt(Long attemptId, JwtPrincipal principal) {
        ExamAttempt attempt = examAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new ApiException(
                        ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.ATTEMPT_NOT_FOUND + attemptId));

        if (!attempt.getStudentId().equals(principal.userId().toString())) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, ExamDeliveryConstants.ATTEMPT_NOT_FOUND + attemptId);
        }
        return attempt;
    }

    /** Rejects access outside the exam's configured availability window (server time only). */
    public void checkAvailabilityWindow(Exam exam) {
        OffsetDateTime now = OffsetDateTime.now();
        if (exam.getAvailabilityStart() != null && now.isBefore(exam.getAvailabilityStart())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, ExamDeliveryConstants.SESSION_NOT_YET_AVAILABLE);
        }
        if (exam.getAvailabilityEnd() != null && now.isAfter(exam.getAvailabilityEnd())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, ExamDeliveryConstants.SESSION_NO_LONGER_AVAILABLE);
        }
    }
}
