package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExaminerWorkflowEntityTest {

    @Test
    void examinerScoreSupportsBoundariesAndOnlyIdenticalRetry() {
        UUID examiner = UUID.randomUUID();
        ExaminerEvaluation zero = score(0, examiner);
        ExaminerEvaluation hundred = score(100, examiner);

        zero.requireIdenticalRetry(examiner, 0);
        hundred.requireIdenticalRetry(examiner, 100);
        assertThat(zero.getScore()).isZero();
        assertThat(hundred.getScore()).isEqualTo(100);
        assertThatThrownBy(() -> score(-1, examiner)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> score(101, examiner)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> zero.requireIdenticalRetry(UUID.randomUUID(), 0))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> zero.requireIdenticalRetry(examiner, 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aDraftBecomesTheSubmittedScoreOfTheSameRowAndThenNeverChanges() {
        UUID examiner = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-23T10:00:00Z");
        ExaminerEvaluation draft = ExaminerEvaluation.draft(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), examiner, 9L, 55, "note", ExaminerDecisionKind.MANUAL, "rv-1", null, now);

        assertThat(draft.getStatus()).isEqualTo(ExaminerAnswerScoreStatus.DRAFT);
        assertThat(draft.getDraftVersion()).isEqualTo(1L);
        assertThat(draft.isPublishable()).isFalse();

        draft.updateDraft(60, "note 2", ExaminerDecisionKind.MANUAL, "rv-1", null, now.plusSeconds(5));
        assertThat(draft.getDraftVersion()).isEqualTo(2L);
        assertThat(draft.getScore()).isEqualTo(60);

        draft.submit(60, now.plusSeconds(10), ExaminerDecisionKind.MANUAL, null, null, "rv-1", UUID.randomUUID(),
                "fingerprint");

        assertThat(draft.isSubmitted()).isTrue();
        assertThat(draft.isPublishable()).isTrue();
        assertThat(draft.getSubmittedAt()).isEqualTo(now.plusSeconds(10));
        assertThatThrownBy(() -> draft.updateDraft(70, null, ExaminerDecisionKind.MANUAL, "rv-1", null, now))
                .isInstanceOf(IllegalStateException.class);
    }

    private ExaminerEvaluation score(int score, UUID examiner) {
        return ExaminerEvaluation.submittedDirectly(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), examiner, score, Instant.now(), null, null, null, null, null, null);
    }
}
