package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExaminerWorkflowEntityTest {

    @Test
    void examinerScoreSupportsBoundariesAndOnlyIdenticalRetry() {
        UUID examiner = UUID.randomUUID();
        ExaminerAnswerScore zero = score(0, examiner);
        ExaminerAnswerScore hundred = score(100, examiner);

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
    void assignmentBatchCommitsTheStoredPreviewAndExpiresOldPreviews() {
        Instant now = Instant.parse("2026-09-23T10:00:00Z");
        ExaminerAssignmentBatch batch = new ExaminerAssignmentBatch(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AssignmentBatchMode.RANDOM, "{}", "{}", UUID.randomUUID(), now.plusSeconds(60));
        assertThat(batch.commit(now)).isTrue();
        assertThat(batch.getStatus()).isEqualTo(AssignmentBatchStatus.COMMITTED);
        assertThat(batch.getCommittedAt()).isEqualTo(now);

        ExaminerAssignmentBatch expired = new ExaminerAssignmentBatch(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), AssignmentBatchMode.MANUAL, "{}", "{}", null, now);
        assertThat(expired.commit(now)).isFalse();
        assertThat(expired.getStatus()).isEqualTo(AssignmentBatchStatus.EXPIRED);
    }

    @Test
    void scoreSourceAuditRequiresScopeSpecificValueAndNonnegativeCount() {
        UUID tenant = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        Instant now = Instant.now();

        ScoreSourceAudit all = new ScoreSourceAudit(tenant, session, actor,
                ScoreSourceSelectionScope.ALL, null, ScoreSource.EXAMINER, 12, now);
        assertThat(all.getAffectedAnswerCount()).isEqualTo(12);
        assertThatThrownBy(() -> new ScoreSourceAudit(tenant, session, actor,
                ScoreSourceSelectionScope.ALL, "SPEAKING", ScoreSource.AI, 1, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreSourceAudit(tenant, session, actor,
                ScoreSourceSelectionScope.SECTION, null, ScoreSource.AI, 1, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreSourceAudit(tenant, session, actor,
                ScoreSourceSelectionScope.TASK_TYPE, "READ_ALOUD", ScoreSource.AI, -1, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private ExaminerAnswerScore score(int score, UUID examiner) {
        return new ExaminerAnswerScore(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                examiner, score, Instant.now());
    }
}
