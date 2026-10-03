package com.pte.scoring.internal.service;

import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
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

    @Mock
    private ScoringMethodResolver methodResolver;

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
        evaluator = new GradingCompletionEvaluator(methodResolver);
    }

    @Test
    void manualModeRejectsAnAssignmentThatCoversFewerSubjectiveItemsThanPinnedCoverage() {
        when(methodResolver.resolve(templateId, "WRITE_ESSAY")).thenReturn(Optional.of(ScoringMethod.AI_TEXT));
        when(methodResolver.resolve(templateId, "SUMMARIZE_WRITTEN_TEXT"))
                .thenReturn(Optional.of(ScoringMethod.AI_TEXT));
        var member = new GradingCohortMember(cohortId, tenantId, sessionId, attemptId, studentId,
                "SUBMITTED", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId, List.of(
                new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY"),
                new AttemptGradingItemView(secondItemId, templateId, "SUMMARIZE_WRITTEN_TEXT")));
        ScoringAnswer first = answer(answerId, itemId, "WRITE_ESSAY");
        ScoringAnswer second = answer(secondAnswerId, secondItemId, "SUMMARIZE_WRITTEN_TEXT");
        ExaminerAttemptAssignment assignment = new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                attemptId, examinerId, 1, UUID.randomUUID(), Instant.now());
        ExaminerAnswerScore firstScore = new ExaminerAnswerScore(answerId, attemptId, sessionId, tenantId, examinerId,
                82, Instant.now());
        ExaminerAnswerScore secondScore = new ExaminerAnswerScore(secondAnswerId, attemptId, sessionId, tenantId,
                examinerId, 76, Instant.now());

        GradingEvaluation result = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(first, second), List.of(assignment),
                List.of(firstScore, secondScore));

        assertThat(result.complete()).isFalse();
        assertThat(result.blockingReasons()).contains(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);
    }

    private ScoringAnswer answer(UUID answerPublicId, UUID pinnedItemPublicId, String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerPublicId);
        answer.setAttemptPublicId(attemptId);
        answer.setPinnedItemPublicId(pinnedItemPublicId);
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        answer.markAiScored(80, AiProviderCategory.REAL, "test", "model", "v1");
        answer.setStatus(ScoringAnswerStatus.SCORED);
        return answer;
    }
}
