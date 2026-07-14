package com.aptis.modules.scoring.service;

import java.util.Collections;
import java.util.List;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.examdelivery.domain.AttemptAnswer;
import com.aptis.modules.examdelivery.domain.ExamAttempt;
import com.aptis.modules.examdelivery.repository.AttemptAnswerRepository;
import com.aptis.modules.examdelivery.repository.ExamAttemptRepository;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.repository.GraderOrgAssignmentRepository;
import com.aptis.modules.scoring.constant.ScoringApiConstants;
import com.aptis.modules.scoring.constant.ScoringConstants;
import com.aptis.modules.scoring.dto.request.GradeAnswerRequest;
import com.aptis.modules.scoring.dto.response.AnswerResponse;
import com.aptis.modules.scoring.dto.response.AttemptResponse;
import com.aptis.modules.scoring.interfaces.GraderScoringOperations;

@Service
@Transactional
public class GraderService implements GraderScoringOperations {

    private final ExamAttemptRepository examAttemptRepository;
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final GraderOrgAssignmentRepository graderOrgAssignmentRepository;

    public GraderService(
            ExamAttemptRepository examAttemptRepository,
            AttemptAnswerRepository attemptAnswerRepository,
            GraderOrgAssignmentRepository graderOrgAssignmentRepository) {
        this.examAttemptRepository = examAttemptRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.graderOrgAssignmentRepository = graderOrgAssignmentRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttemptResponse> listAttempts(Long graderId) {
        List<Long> orgIds = getScopeOrgs(graderId);
        if (orgIds.isEmpty()) {
            return Collections.emptyList();
        }
        return examAttemptRepository.findSubmittedByOrgIds(orgIds).stream()
                .map(this::toAttemptResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AnswerResponse> getAttemptAnswers(Long graderId, Long attemptId) {
        validateAttemptScope(graderId, attemptId);
        return attemptAnswerRepository.findAllByAttemptId(attemptId).stream()
                .map(this::toAnswerResponse)
                .toList();
    }

    @Override
    public void gradeAnswer(Long graderId, Long attemptId, Long answerId, GradeAnswerRequest request) {
        validateAttemptScope(graderId, attemptId);
        AttemptAnswer answer = attemptAnswerRepository.findById(answerId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, ScoringApiConstants.ANSWER_NOT_FOUND));
        if (!answer.getAttempt().getId().equals(attemptId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, ScoringApiConstants.ANSWER_NOT_FOUND);
        }
        if (ScoringConstants.QUESTION_TYPE_MC.equals(answer.getQuestionType())) {
            if (request.getIsCorrect() != null) {
                answer.setIsCorrect(request.getIsCorrect());
            }
        } else if (ScoringConstants.QUESTION_TYPE_SPEAKING.equals(answer.getQuestionType())
                || ScoringConstants.QUESTION_TYPE_WRITING.equals(answer.getQuestionType())) {
            if (request.getManualScore() != null) {
                answer.setManualScore(request.getManualScore());
            }
        }
        try {
            attemptAnswerRepository.save(answer);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, ScoringApiConstants.ANSWER_CONCURRENT_MODIFICATION);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> getScopeOrgs(Long graderId) {
        return graderOrgAssignmentRepository.findByIdGraderId(graderId).stream()
                .map(a -> a.getId().getOrganizationId())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public void validateAttemptScope(Long graderId, Long attemptId) {
        ExamAttempt attempt = examAttemptRepository.findByIdWithExam(attemptId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, ScoringApiConstants.ATTEMPT_NOT_FOUND));
        List<Long> orgIds = getScopeOrgs(graderId);
        Long attemptOrgId = attempt.getExam().getOrganizationId();
        if (!orgIds.contains(attemptOrgId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, IamApiConstants.GRADER_ORG_NOT_ASSIGNED);
        }
    }

    private AttemptResponse toAttemptResponse(ExamAttempt attempt) {
        return AttemptResponse.builder()
                .id(attempt.getId())
                .studentId(attempt.getStudentId())
                .examId(attempt.getExamId())
                .isSubmitted(attempt.getIsSubmitted())
                .submittedAt(attempt.getSubmittedAt())
                .answerCount(attempt.getAnswers().size())
                .build();
    }

    private AnswerResponse toAnswerResponse(AttemptAnswer answer) {
        return AnswerResponse.builder()
                .id(answer.getId())
                .questionId(answer.getQuestionId())
                .questionType(answer.getQuestionType())
                .content(answer.getContent())
                .selectedOptionId(answer.getSelectedOptionId())
                .isCorrect(answer.getIsCorrect())
                .manualScore(answer.getManualScore())
                .build();
    }
}
