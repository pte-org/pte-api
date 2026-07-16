package com.aptis.modules.examdelivery.service;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.AttemptAnswer;
import com.aptis.modules.examdelivery.domain.ExamAttemptStatus;
import com.aptis.modules.examdelivery.domain.RetryRequest;
import com.aptis.modules.examdelivery.domain.RetryRequestStatus;
import com.aptis.modules.examdelivery.dto.ExamAttemptResponse;
import com.aptis.modules.examdelivery.dto.HeartbeatResponse;
import com.aptis.modules.examdelivery.dto.SubmitAnswerRequest;
import com.aptis.modules.examdelivery.dto.SubmitExamRequest;
import com.aptis.modules.examdelivery.interfaces.ExamDeliveryOperations;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;
import com.aptis.modules.examdelivery.repository.RetryRequestRepository;
import com.aptis.modules.proctor.service.ProctorStatusPushService;

import java.time.ZoneOffset;
import java.util.Optional;

@Service
public class ExamAttemptService implements ExamDeliveryOperations {

    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ExamAttemptAccessGuard accessGuard;
    private final ProctorStatusPushService proctorStatusPushService;
    private final RetryRequestRepository retryRequestRepository;

    public ExamAttemptService(
            AttemptAnswerRepository attemptAnswerRepository,
            ExamAttemptAccessGuard accessGuard,
            ProctorStatusPushService proctorStatusPushService,
            RetryRequestRepository retryRequestRepository) {
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.accessGuard = accessGuard;
        this.proctorStatusPushService = proctorStatusPushService;
        this.retryRequestRepository = retryRequestRepository;
    }

    @Transactional
    public void submitAnswer(Long attemptId, SubmitAnswerRequest request, JwtPrincipal principal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, principal);
        accessGuard.checkAvailabilityWindow(attempt.getExam());

        if (attempt.getIsSubmitted()) {
            throw new ApiException(
                    ErrorCode.RESOURCE_CONFLICT,
                    ExamDeliveryConstants.ATTEMPT_ALREADY_SUBMITTED
            );
        }

        Optional<AttemptAnswer> existingAnswerOpt = attemptAnswerRepository
                .findByAttemptIdAndQuestionId(attemptId, request.questionId());

        if (existingAnswerOpt.isPresent()) {
            AttemptAnswer existingAnswer = existingAnswerOpt.get();
            existingAnswer.setQuestionType(request.questionType());
            existingAnswer.setContent(request.content());
            attemptAnswerRepository.save(existingAnswer);
        } else {
            AttemptAnswer newAnswer = new AttemptAnswer(
                    attempt,
                    request.questionId(),
                    request.questionType(),
                    request.content()
            );
            attemptAnswerRepository.save(newAnswer);
        }
    }

    /**
     * Lightweight: one locked read + one write, no extra queries beyond the status update.
     * Isolation is explicit (not assumed from the DB default) because the NOT_STARTED branch
     * reads RetryRequest state via {@link #enforceRetryGate} — Spring ignores an isolation
     * level declared on a nested {@code @Transactional} call joining this one, so it must be
     * set here, on the outermost transactional boundary (Phase 9 Design Constraint).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HeartbeatResponse recordHeartbeat(Long attemptId, JwtPrincipal principal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, principal);
        if (attempt.getStatus() == ExamAttemptStatus.NOT_STARTED) {
            enforceRetryGate(attempt);
        }
        var status = attempt.recordHeartbeat();
        pushStatus(attempt);
        return new HeartbeatResponse(status.name(), attempt.getLastHeartbeatAt());
    }

    /**
     * Exam-start gating check (Phase 9 Design Constraint): a PENDING retry request for this
     * student+exam blocks the attempt from starting until reviewed; an APPROVED one is
     * consumed exactly once, here, at the moment the retried attempt actually starts.
     */
    private void enforceRetryGate(ExamAttempt attempt) {
        Optional<RetryRequest> mostRecent =
                retryRequestRepository.findMostRecent(attempt.getStudentId(), attempt.getExamId());
        if (mostRecent.isEmpty()) {
            return;
        }
        RetryRequest request = mostRecent.get();
        if (request.getStatus() == RetryRequestStatus.PENDING) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, ExamDeliveryConstants.RETRY_PENDING_BLOCKS_START);
        }
        if (request.getStatus() == RetryRequestStatus.APPROVED) {
            request.consume();
            retryRequestRepository.save(request);
        }
    }

    @Transactional
    public ExamAttemptResponse switchSection(Long attemptId, JwtPrincipal principal) {
        ExamAttempt attempt = accessGuard.loadOwnedAttempt(attemptId, principal);
        attempt.switchSection();
        pushStatus(attempt);
        return toResponse(attempt);
    }

    @Override
    @Transactional
    public ExamAttemptResponse submitAttempt(SubmitExamRequest request, JwtPrincipal principal) {
        // Pessimistic lock: a concurrent proctor force-submit (Phase 8) and this student
        // submit must not both apply.
        ExamAttempt attempt = accessGuard.loadOwnedAttemptForUpdate(request.attemptId(), principal);
        attempt.submit();
        pushStatus(attempt);
        return toResponse(attempt);
    }

    private void pushStatus(ExamAttempt attempt) {
        proctorStatusPushService.pushStatus(
                attempt.getExamId(),
                attempt.getId(),
                attempt.getStudentId(),
                attempt.getStatus().name(),
                attempt.getLastHeartbeatAt(),
                attempt.getSubmittedAt() != null ? attempt.getSubmittedAt().atOffset(ZoneOffset.UTC) : null);
    }

    private ExamAttemptResponse toResponse(ExamAttempt attempt) {
        return new ExamAttemptResponse(
                attempt.getId(),
                attempt.getStudentId(),
                attempt.getExamId(),
                attempt.getIsSubmitted(),
                attempt.getStatus().name());
    }
}
