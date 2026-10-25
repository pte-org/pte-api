package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.GradingConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GradingCompletionEvaluatorAssignmentCoverageTest {

    private GradingCompletionEvaluator evaluator;
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID cohortId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();
    private final UUID itemId = UUID.randomUUID();
    private final UUID secondItemId = UUID.randomUUID();
    private final UUID answerId = UUID.randomUUID();
    private final UUID secondAnswerId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new GradingCompletionEvaluator();
    }

    @Test
    void manualModeRejectsAnAssignmentThatCoversFewerSubjectiveItemsThanPinnedCoverage() {
        var member = new AttemptGradingCandidateView(attemptId, studentId,
                "SUBMITTED", "v1", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId, List.of(
                new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY"),
                new AttemptGradingItemView(secondItemId, templateId, "SUMMARIZE_WRITTEN_TEXT")));
        AiEvaluation first = answer(answerId, itemId, "WRITE_ESSAY");
        AiEvaluation second = answer(secondAnswerId, secondItemId, "SUMMARIZE_WRITTEN_TEXT");
        GradingAssignment assignment = new GradingAssignment(tenantId, sessionId,
                attemptId, examinerId, 1, UUID.randomUUID(), Instant.now());
        ExaminerEvaluation firstScore = ExaminerEvaluation.submittedDirectly(tenantId, sessionId, attemptId,
                answerId, examinerId, 82, Instant.now(), null, null, null, null, null, null);
        ExaminerEvaluation secondScore = ExaminerEvaluation.submittedDirectly(tenantId, sessionId, attemptId,
                secondAnswerId, examinerId, 76, Instant.now(), null, null, null, null, null, null);

        GradingEvaluation result = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(first, second), List.of(assignment),
                List.of(firstScore, secondScore), Map.of());

        assertThat(result.complete()).isFalse();
        assertThat(result.blockingReasons()).contains(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);
    }

    private AiEvaluation answer(UUID answerPublicId, UUID itemPublicId, String taskType) {
        AiEvaluation answer = new AiEvaluation(tenantId, sessionId, attemptId, answerPublicId, itemPublicId,
                templateId, taskType, "WRITING", ScoringMethod.AI_TEXT);
        answer.markAiScored(80, AiProviderCategory.REAL, "test", "model", "v1");
        answer.setStatus(ScoringAnswerStatus.SCORED);
        return answer;
    }
}
