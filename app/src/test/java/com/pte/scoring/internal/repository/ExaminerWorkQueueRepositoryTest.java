package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false"
})
class ExaminerWorkQueueRepositoryTest {

    @Autowired
    private ExaminerWorkQueueRepository repository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void ordinalCountsOnlyTheExaminersEarlierAssignmentsInTheSameSession() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        Instant base = Instant.parse("2026-10-01T00:00:00Z");
        ExaminerAttemptAssignment first = new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                UUID.randomUUID(), examinerId, 1, UUID.randomUUID(), base);
        ExaminerAttemptAssignment second = new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                UUID.randomUUID(), examinerId, 1, UUID.randomUUID(), base.plusSeconds(60));
        entityManager.persist(first);
        entityManager.persist(second);
        entityManager.persist(new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                UUID.randomUUID(), UUID.randomUUID(), 1, UUID.randomUUID(), base.minusSeconds(60)));
        entityManager.flush();

        assertThat(repository.countOrdinal(tenantId, examinerId, sessionId, first.getAssignedAt(), first.getId()))
                .isEqualTo(1);
        assertThat(repository.countOrdinal(tenantId, examinerId, sessionId, second.getAssignedAt(), second.getId()))
                .isEqualTo(2);
    }

    @Test
    void queueHidesAssignmentsOnceTheSessionIsPublished() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        entityManager.persist(new ExaminerAttemptAssignment(UUID.randomUUID(), tenantId, sessionId,
                UUID.randomUUID(), examinerId, 2, UUID.randomUUID(), Instant.now()));
        entityManager.flush();

        Page<ExaminerQueueItemResponse> beforePublication = repository.findQueue(tenantId, examinerId,
                sessionId, "ALL", PageRequest.of(0, 20));
        assertThat(beforePublication.getTotalElements()).isEqualTo(1);

        ScoringSessionState state = new ScoringSessionState(tenantId, sessionId);
        state.lockForPublication(UUID.randomUUID(), Instant.now());
        entityManager.persist(state);
        entityManager.flush();

        Page<ExaminerQueueItemResponse> afterPublication = repository.findQueue(tenantId, examinerId,
                sessionId, "ALL", PageRequest.of(0, 20));
        assertThat(afterPublication.getTotalElements()).isZero();
    }
}
