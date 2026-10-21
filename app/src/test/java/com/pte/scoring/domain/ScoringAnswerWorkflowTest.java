package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringAnswerWorkflowTest {

    @Test
    void legacyOrStubRawScoreCannotBeSelectedAsAiSource() {
        ScoringAnswer legacy = new ScoringAnswer();
        assertThatThrownBy(() -> legacy.markScored(101)).isInstanceOf(IllegalArgumentException.class);
        legacy.markScored(70);
        assertThatThrownBy(() -> legacy.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now(), null))
                .isInstanceOf(IllegalStateException.class);

        ScoringAnswer stub = new ScoringAnswer();
        stub.markAiScored(65, AiProviderCategory.STUB, "STUB", null, null);
        assertThatThrownBy(() -> stub.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now(), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void realAiProvenanceIsStoredWithTheRawScoreAndCanBeSelected() {
        ScoringAnswer answer = answerWithIdentity();
        answer.markAiScored(82, AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "model-v2", null);
        UUID actor = UUID.randomUUID();
        Instant selectedAt = Instant.parse("2026-09-23T10:00:00Z");

        answer.selectScoreSource(ScoreSource.AI, actor, selectedAt, null);

        assertThat(answer.getRawScore()).isEqualTo(82);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(answer.getAiProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(answer.getAiModel()).isEqualTo("model-v2");
        assertThat(answer.getSelectedScoreSource()).isEqualTo(ScoreSource.AI);
        assertThat(answer.getSelectedScoreSourceByPublicId()).isEqualTo(actor);
        assertThat(answer.getSelectedScoreSourceAt()).isEqualTo(selectedAt);
        assertThat(answer.getSelectedExaminerScorePublicId()).isNull();
        assertThat(answer.getTeacherScore()).isNull();
    }

    @Test
    void examinerSourceRequiresPersistedScoreForTheSameAnswerAndOwnership() {
        ScoringAnswer answer = answerWithIdentity();
        UUID actor = UUID.randomUUID();
        Instant selectedAt = Instant.parse("2026-09-23T10:00:00Z");

        ExaminerAnswerScore transientScore = examinerScoreFor(answer);
        assertThatThrownBy(() -> answer.selectScoreSource(ScoreSource.EXAMINER, actor, selectedAt, transientScore))
                .isInstanceOf(IllegalStateException.class);

        ExaminerAnswerScore differentAnswer = persisted(examinerScoreFor(UUID.randomUUID(),
                answer.getAttemptPublicId(), answer.getSessionPublicId(), answer.getTenantId()));
        assertThatThrownBy(() -> answer.selectScoreSource(ScoreSource.EXAMINER, actor, selectedAt, differentAnswer))
                .isInstanceOf(IllegalStateException.class);

        ExaminerAnswerScore differentAttempt = persisted(examinerScoreFor(answer.getAnswerPublicId(),
                UUID.randomUUID(), answer.getSessionPublicId(), answer.getTenantId()));
        assertThatThrownBy(() -> answer.selectScoreSource(ScoreSource.EXAMINER, actor, selectedAt, differentAttempt))
                .isInstanceOf(IllegalStateException.class);

        ExaminerAnswerScore submittedScore = persisted(examinerScoreFor(answer));
        answer.selectScoreSource(ScoreSource.EXAMINER, actor, selectedAt, submittedScore);

        assertThat(answer.getSelectedScoreSource()).isEqualTo(ScoreSource.EXAMINER);
        assertThat(answer.getSelectedExaminerScorePublicId()).isEqualTo(submittedScore.getPublicId());

        answer.markAiScored(82, AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "model-v2", null);
        answer.selectScoreSource(ScoreSource.AI, actor, selectedAt, null);
        assertThat(answer.getSelectedExaminerScorePublicId()).isNull();
    }

    @Test
    void eachAiDispatchIssuesANewTokenAndOnlyTheLatestAwaitsAResult() {
        ScoringAnswer answer = answerWithIdentity();
        Instant firstDispatch = Instant.parse("2026-10-11T10:00:00Z");
        Instant secondDispatch = Instant.parse("2026-10-11T10:20:00Z");

        long first = answer.markAiDispatched(firstDispatch);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(answer.getAiDispatchedAt()).isEqualTo(firstDispatch);
        assertThat(answer.isAwaitingAiResult(first)).isTrue();

        long second = answer.markAiDispatched(secondDispatch);

        assertThat(second).isGreaterThan(first);
        assertThat(answer.getAiDispatchedAt()).isEqualTo(secondDispatch);
        assertThat(answer.isAwaitingAiResult(first)).isFalse();
        assertThat(answer.isAwaitingAiResult(second)).isTrue();

        answer.markAiScored(70, AiProviderCategory.REAL, "AZURE_GEMINI", "model", null);
        assertThat(answer.isAwaitingAiResult(second)).isFalse();
    }

    @Test
    void rescoringAFailedAnswerKeepsTheHostsExaminerSelection() {
        ScoringAnswer answer = answerWithIdentity();
        ExaminerAnswerScore examinerScore = persisted(examinerScoreFor(answer));
        UUID actor = UUID.randomUUID();
        Instant selectedAt = Instant.parse("2026-10-11T09:00:00Z");
        answer.selectScoreSource(ScoreSource.EXAMINER, actor, selectedAt, examinerScore);
        answer.setStatus(ScoringAnswerStatus.SCORING_FAILED);

        answer.markAiDispatched(Instant.parse("2026-10-11T10:00:00Z"));
        answer.markAiScored(70, AiProviderCategory.REAL, "AZURE_GEMINI", "model", null);

        assertThat(answer.getSelectedScoreSource()).isEqualTo(ScoreSource.EXAMINER);
        assertThat(answer.getSelectedExaminerScorePublicId()).isEqualTo(examinerScore.getPublicId());
        assertThat(answer.getSelectedScoreSourceByPublicId()).isEqualTo(actor);
        assertThat(answer.getSelectedScoreSourceAt()).isEqualTo(selectedAt);
    }

    @Test
    void gradingCohortReportsCompletionOnlyOnce() {
        // GradingCohortService publishes SessionGradingCompletedEvent only when complete() returns true,
        // so a late AI rescore that triggers another reconcile cannot announce completion twice.
        GradingCohort cohort = new GradingCohort(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "preview-v1", GradingMarkingMode.AI_ONLY, Instant.parse("2026-10-11T08:00:00Z"));
        Instant completedAt = Instant.parse("2026-10-11T10:00:00Z");

        assertThat(cohort.complete(completedAt)).isTrue();
        assertThat(cohort.complete(Instant.parse("2026-10-11T11:00:00Z"))).isFalse();
        assertThat(cohort.getCompletedAt()).isEqualTo(completedAt);
    }

    private ScoringAnswer answerWithIdentity() {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        return answer;
    }

    private ExaminerAnswerScore examinerScoreFor(ScoringAnswer answer) {
        return examinerScoreFor(answer.getAnswerPublicId(), answer.getAttemptPublicId(),
                answer.getSessionPublicId(), answer.getTenantId());
    }

    private ExaminerAnswerScore examinerScoreFor(UUID answerPublicId, UUID attemptPublicId,
            UUID sessionPublicId, UUID tenantId) {
        return new ExaminerAnswerScore(answerPublicId, attemptPublicId, sessionPublicId,
                tenantId, UUID.randomUUID(), 75, Instant.parse("2026-09-23T09:00:00Z"));
    }

    private ExaminerAnswerScore persisted(ExaminerAnswerScore score) {
        score.setId(1L);
        score.setPublicId(UUID.randomUUID());
        return score;
    }
}
