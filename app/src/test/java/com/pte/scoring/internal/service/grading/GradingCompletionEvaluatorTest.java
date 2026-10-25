package com.pte.scoring.internal.service.grading;

import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.attempt.dto.response.AttemptGradingItemView;
import com.pte.scoring.internal.constant.GradingConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class GradingCompletionEvaluatorTest {

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
        evaluator = new GradingCompletionEvaluator();
    }

    @Test
    void manualModeRequiresCommittedAssignmentAndSubmittedExaminerScoreForEverySubjectiveItem() {
        var member = new AttemptGradingCandidateView(attemptId, studentId,
                "SUBMITTED", "v1", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId,
                List.of(new AttemptGradingItemView(itemId, templateId, "WRITE_ESSAY")));
        AiEvaluation answer = answer(ScoringAnswerStatus.SCORED, "WRITE_ESSAY");

        GradingEvaluation blocked = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(answer), List.of(), List.of(), Map.of());

        assertThat(blocked.complete()).isFalse();
        assertThat(blocked.blockingReasons()).contains(GradingConstants.EXAMINER_ASSIGNMENT_REQUIRED);

        GradingAssignment assignment = new GradingAssignment(tenantId, sessionId,
                attemptId, examinerId, 1, UUID.randomUUID(), Instant.now());
        ExaminerEvaluation score = ExaminerEvaluation.submittedDirectly(tenantId, sessionId, attemptId, answerId,
                examinerId, 82, Instant.now(), null, null, null, null, null, null);
        GradingEvaluation complete = evaluator.evaluate(GradingMarkingMode.MANUAL_EXAMINER, List.of(member),
                Map.of(attemptId, coverage), List.of(answer), List.of(assignment), List.of(score), Map.of());

        assertThat(complete.complete()).isTrue();
        assertThat(complete.satisfiedItemCount()).isEqualTo(1);
    }

    @Test
    void aiOnlyRequiresRealAiScoreAndDoesNotTreatFailedOrStubResultsAsComplete() {
        var member = new AttemptGradingCandidateView(attemptId, studentId,
                "SUBMITTED", "v1", false, null);
        var coverage = new AttemptGradingCoverageView(attemptId,
                List.of(new AttemptGradingItemView(itemId, templateId, "SUMMARIZE_WRITTEN_TEXT")));

        GradingEvaluation pending = evaluator.evaluate(GradingMarkingMode.AI_ONLY, List.of(member),
                Map.of(attemptId, coverage), List.of(answer(ScoringAnswerStatus.SCORING_FAILED,
                        "SUMMARIZE_WRITTEN_TEXT")), List.of(), List.of(), Map.of());

        assertThat(pending.complete()).isFalse();
        assertThat(pending.blockingReasons()).contains(GradingConstants.AI_SCORE_REQUIRED);
    }

    private AiEvaluation answer(ScoringAnswerStatus status, String taskType) {
        AiEvaluation answer = new AiEvaluation(tenantId, sessionId, attemptId, answerId, itemId, templateId,
                taskType, "WRITING", ScoringMethod.AI_TEXT);
        answer.markAiScored(80, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        answer.setStatus(status);
        return answer;
    }
}
