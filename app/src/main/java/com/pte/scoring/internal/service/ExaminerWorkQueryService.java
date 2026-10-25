package com.pte.scoring.internal.service;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.dto.response.ExaminerScoringWorkItemView;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Produces a blind work projection after checking tenant/session/attempt assignment ownership. */
@Service
public class ExaminerWorkQueryService {

    private final GradingAssignmentRepository assignmentRepository;
    private final ExaminerEvaluationRepository examinerEvaluationRepository;
    private final AiEvaluationRepository scoringAnswerRepository;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerContentReader contentReader;

    public ExaminerWorkQueryService(GradingAssignmentRepository assignmentRepository,
            ExaminerEvaluationRepository examinerEvaluationRepository,
            AiEvaluationRepository scoringAnswerRepository,
            ScoringEligibilityQueryService eligibilityQueryService, AnswerContentReader contentReader) {
        this.assignmentRepository = assignmentRepository;
        this.examinerEvaluationRepository = examinerEvaluationRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.eligibilityQueryService = eligibilityQueryService;
        this.contentReader = contentReader;
    }

    @Transactional(readOnly = true)
    public boolean isAssignedToExaminer(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID examinerPublicId) {
        return assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                tenantId, sessionPublicId, attemptPublicId, examinerPublicId);
    }

    @Transactional(readOnly = true)
    public List<UUID> findAssignedExaminerPublicIds(UUID tenantId, UUID sessionPublicId) {
        return assignmentRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId)
                .stream().map(GradingAssignment::getExaminerPublicId).distinct().sorted().toList();
    }

    @Transactional(readOnly = true)
    public List<ExaminerScoringWorkItemView> findWorkItems(UUID tenantId, UUID sessionPublicId,
            UUID examinerPublicId) {
        List<GradingAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndExaminerPublicId(tenantId, sessionPublicId, examinerPublicId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> attemptIds = assignments.stream().map(GradingAssignment::getAttemptPublicId)
                .collect(Collectors.toSet());
        Set<UUID> alreadyScoredAnswerIds = examinerEvaluationRepository
                .findSubmittedBySession(tenantId, sessionPublicId).stream()
                .map(score -> score.getAnswerPublicId()).collect(Collectors.toSet());
        Map<UUID, SubmittedAnswerView> content = contentReader.forSession(sessionPublicId, tenantId);
        return scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
                        sessionPublicId, tenantId, List.copyOf(attemptIds)).stream()
                .filter(eligibilityQueryService::isAiEligible)
                .filter(answer -> !alreadyScoredAnswerIds.contains(answer.getAnswerPublicId()))
                .map(answer -> toWorkItem(answer, content.get(answer.getAnswerPublicId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<ExaminerScoringWorkItemView> findWorkItem(UUID tenantId, UUID examinerPublicId,
            UUID answerPublicId) {
        return scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .filter(answer -> answer.getTenantId().equals(tenantId))
                .filter(answer -> isAssignedToExaminer(tenantId, answer.getSessionPublicId(),
                        answer.getAttemptPublicId(), examinerPublicId))
                .filter(eligibilityQueryService::isAiEligible)
                .filter(answer -> examinerEvaluationRepository.findSubmitted(answerPublicId, tenantId).isEmpty())
                .map(answer -> toWorkItem(answer, contentReader.one(answerPublicId, tenantId).orElse(null)));
    }

    private static ExaminerScoringWorkItemView toWorkItem(AiEvaluation answer, SubmittedAnswerView content) {
        return new ExaminerScoringWorkItemView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getItemPublicId(), answer.getTaskType(),
                content == null ? null : content.payload());
    }
}
