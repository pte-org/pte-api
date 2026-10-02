package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.dto.response.ExaminerScoringWorkItemView;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Produces a blind work projection after checking tenant/session/attempt assignment ownership. */
@Service
public class ExaminerWorkQueryService {

    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ScoringEligibilityQueryService eligibilityQueryService;

    public ExaminerWorkQueryService(ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ScoringEligibilityQueryService eligibilityQueryService) {
        this.assignmentRepository = assignmentRepository;
        this.examinerAnswerScoreRepository = examinerAnswerScoreRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.eligibilityQueryService = eligibilityQueryService;
    }

    @Transactional(readOnly = true)
    public boolean isAssignedToExaminer(UUID tenantId, UUID sessionPublicId, UUID attemptPublicId,
            UUID examinerPublicId) {
        return assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                tenantId, sessionPublicId, attemptPublicId, examinerPublicId);
    }

    @Transactional(readOnly = true)
    public List<ExaminerScoringWorkItemView> findWorkItems(UUID tenantId, UUID sessionPublicId,
            UUID examinerPublicId) {
        List<ExaminerAttemptAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndExaminerPublicId(tenantId, sessionPublicId, examinerPublicId);
        if (assignments.isEmpty()) {
            return List.of();
        }
        Set<UUID> attemptIds = assignments.stream().map(ExaminerAttemptAssignment::getAttemptPublicId)
                .collect(Collectors.toSet());
        Set<UUID> alreadyScoredAnswerIds = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .map(score -> score.getAnswerPublicId()).collect(Collectors.toSet());
        return scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(
                        sessionPublicId, tenantId, List.copyOf(attemptIds)).stream()
                .filter(eligibilityQueryService::isAiEligible)
                .filter(answer -> !alreadyScoredAnswerIds.contains(answer.getAnswerPublicId()))
                .map(ExaminerWorkQueryService::toWorkItem)
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
                .filter(answer -> examinerAnswerScoreRepository
                        .findByAnswerPublicIdAndTenantId(answerPublicId, tenantId).isEmpty())
                .map(ExaminerWorkQueryService::toWorkItem);
    }

    private static ExaminerScoringWorkItemView toWorkItem(ScoringAnswer answer) {
        return new ExaminerScoringWorkItemView(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getPinnedItemPublicId(), answer.getTaskType(), answer.getPayload());
    }
}
