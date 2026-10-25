package com.pte.attempt.internal.repository;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The presence ping is a targeted update: it stamps the column and never touches the optimistic version. */
@DataJpaTest
class ExamAttemptLastSeenTest {

    @Autowired
    private ExamAttemptRepository attempts;

    @Autowired
    private EntityManager entityManager;

    @Test
    void touchLastSeen_stampsTheAttemptWithoutBumpingTheVersion() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setTenantId(UUID.randomUUID());
        attempt.setSessionPublicId(UUID.randomUUID());
        attempt.setStudentPublicId(UUID.randomUUID());
        attempt.setStatus(AttemptStatus.IN_PROGRESS);
        attempt.setExamVersionPublicId(UUID.randomUUID());
        attempt.setExamDefinitionPublicId(UUID.randomUUID());
        ExamAttempt saved = attempts.saveAndFlush(attempt);
        Long versionBefore = saved.getVersion();
        Instant seenAt = Instant.parse("2026-10-25T01:02:03Z");

        int updated = attempts.touchLastSeen(saved.getId(), seenAt);
        entityManager.clear();

        ExamAttempt reloaded = attempts.findById(saved.getId()).orElseThrow();
        assertThat(updated).isEqualTo(1);
        assertThat(reloaded.getLastSeenAt()).isEqualTo(seenAt);
        assertThat(reloaded.getVersion()).isEqualTo(versionBefore);
    }

    @Test
    void touchLastSeen_onAMissingAttemptUpdatesNothing() {
        assertThat(attempts.touchLastSeen(-1L, Instant.now())).isZero();
    }
}
