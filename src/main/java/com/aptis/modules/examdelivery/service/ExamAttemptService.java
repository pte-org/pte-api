package com.aptis.modules.examdelivery.service;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;
import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.examdelivery.constant.ExamDeliveryConstants;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.domain.AttemptAnswer;
import com.aptis.modules.examdelivery.dto.SubmitAnswerRequest;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;

import java.util.Optional;

@Service
public class ExamAttemptService {

    private final AttemptAnswerRepository attemptAnswerRepository;
    private final ExamAttemptAccessGuard accessGuard;

    public ExamAttemptService(
            AttemptAnswerRepository attemptAnswerRepository,
            ExamAttemptAccessGuard accessGuard) {
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.accessGuard = accessGuard;
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
}
