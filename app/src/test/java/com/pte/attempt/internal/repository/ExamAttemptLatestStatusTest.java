package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Whole-session status counts read each student's latest attempt only, and only for the students asked about. */
@DataJpaTest
class ExamAttemptLatestStatusTest {

    @Autowired
    private ExamAttemptRepository attempts;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();

    private void attempt(UUID student, int number, AttemptStatus status, boolean deleted) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(tenantId);
        attempt.setSessionPublicId(sessionId);
        attempt.setStudentPublicId(student);
        attempt.setAttemptNumber(number);
        attempt.setStatus(status);
        attempt.setDeleted(deleted);
        attempt.setExamVersionPublicId(UUID.randomUUID());
        attempt.setExamDefinitionPublicId(UUID.randomUUID());
        attempts.saveAndFlush(attempt);
    }

    @Test
    void returnsOnlyTheLatestLiveAttemptOfEachRequestedStudent() {
        UUID retried = UUID.randomUUID();
        UUID single = UUID.randomUUID();
        UUID notRequested = UUID.randomUUID();
        UUID neverStarted = UUID.randomUUID();
        attempt(retried, 1, AttemptStatus.SUBMITTED, false);
        attempt(retried, 2, AttemptStatus.IN_PROGRESS, false);
        attempt(retried, 3, AttemptStatus.SUBMITTED, true);
        attempt(single, 1, AttemptStatus.CREATED, false);
        attempt(notRequested, 1, AttemptStatus.IN_PROGRESS, false);

        List<ExamAttemptRepository.LatestStatusRow> rows = attempts.findLatestStatuses(sessionId, tenantId,
                List.of(retried, single, neverStarted));

        assertThat(rows).extracting(ExamAttemptRepository.LatestStatusRow::getStudentPublicId)
                .containsExactlyInAnyOrder(retried, single);
        assertThat(rows).filteredOn(row -> row.getStudentPublicId().equals(retried))
                .extracting(ExamAttemptRepository.LatestStatusRow::getStatus)
                .containsExactly(AttemptStatus.IN_PROGRESS);
    }

    @Test
    void anotherTenantsAttemptsNeverCount() {
        UUID student = UUID.randomUUID();
        attempt(student, 1, AttemptStatus.SUBMITTED, false);

        assertThat(attempts.findLatestStatuses(sessionId, UUID.randomUUID(), List.of(student))).isEmpty();
    }
}
