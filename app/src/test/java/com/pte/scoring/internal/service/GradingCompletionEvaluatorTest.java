package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
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
class GradingCompletionEvaluatorTest {

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
    private final UUID answerId = UUID.randomUUID();
    private final UUID examinerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        evaluator = new GradingCompletionEvaluator(methodResolver);
    }

    @Test
    void manualModeRequiresCommittedAssignmentAndSubmittedExaminerScoreForEverySubjectiveItem() {
        when(methodResolver.resolve(templateId, "WRITE_ESSAY")).thenReturn(Optional.of(ScoringMethod.AI_TEXT));
        var member = new GradingCohortMember(cohortId, tenantId, sessionId, attemptId, studentId,
                "SUBMITTED", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId,
                List.of(new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY")));
        ScoringAnswer answer = answer(ScoringAnswerStatus.SCORED, "WRITE_ESSAY");

        GradingEvaluation blocked = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(answer), List.of(), List.of());

        assertThat(blocked.complete()).isFalse();
        assertThat(blocked.blockingReasons()).contains(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);

        ExaminerAttemptAssignment assignment = new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                attemptId, examinerId, 1, UUID.randomUUID(), Instant.now());
        ExaminerAnswerScore score = new ExaminerAnswerScore(answerId, attemptId, sessionId, tenantId, examinerId,
                82, Instant.now());
        GradingEvaluation complete = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(answer), List.of(assignment), List.of(score));

        assertThat(complete.complete()).isTrue();
        assertThat(complete.satisfiedItemCount()).isEqualTo(1);
    }

    @Test
    void aiOnlyRequiresRealAiScoreAndDoesNotTreatFailedOrStubResultsAsComplete() {
        when(methodResolver.resolve(templateId, "SUMMARIZE_WRITTEN_TEXT"))
                .thenReturn(Optional.of(ScoringMethod.AI_TEXT));
        var member = new GradingCohortMember(cohortId, tenantId, sessionId, attemptId, studentId,
                "SUBMITTED", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId,
                List.of(new AttemptGradingItemView(itemId, templateId, "SUMMARIZE_WRITTEN_TEXT")));

        GradingEvaluation pending = evaluator.evaluate(GradingMarkingMode.AI_ONLY, List.of(member),
                Map.of(attemptId, coverage), List.of(answer(ScoringAnswerStatus.SCORING_FAILED,
                        "SUMMARIZE_WRITTEN_TEXT")), List.of(), List.of());

        assertThat(pending.complete()).isFalse();
        assertThat(pending.blockingReasons()).contains(GradingConstants.AI_SCORE_REQUIRED);
    }

    private ScoringAnswer answer(ScoringAnswerStatus status, String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(answerId);
        answer.setAttemptPublicId(attemptId);
        answer.setPinnedItemPublicId(itemId);
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        answer.setStatus(status);
        answer.setRawScore(80);
        return answer;
    }
}
