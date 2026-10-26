package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExaminerAssignmentRepositoryTest {

    @Autowired
    private GradingAssignmentRepository assignmentRepository;

    @Test
    void committedAssignmentLoadsAreAggregatedFromPersistedAnswerCounts() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID examinerA = UUID.randomUUID();
        UUID examinerB = UUID.randomUUID();
        assignmentRepository.saveAllAndFlush(List.of(
                assignment(tenantId, sessionId, hostId, examinerA, 2),
                assignment(tenantId, sessionId, hostId, examinerA, 3),
                assignment(tenantId, sessionId, hostId, examinerB, 5)));

        var loads = assignmentRepository.summarizeLoads(tenantId, sessionId);

        assertThat(assignmentRepository.countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionId))
                .isEqualTo(3);
        assertThat(loads).hasSize(2);
        assertThat(loads).anySatisfy(load -> {
            assertThat(load.getExaminerPublicId()).isEqualTo(examinerA);
            assertThat(load.getAttemptCount()).isEqualTo(2);
            assertThat(load.getEligibleAnswerCount()).isEqualTo(5);
        });
        assertThat(loads).anySatisfy(load -> {
            assertThat(load.getExaminerPublicId()).isEqualTo(examinerB);
            assertThat(load.getAttemptCount()).isEqualTo(1);
            assertThat(load.getEligibleAnswerCount()).isEqualTo(5);
        });
    }

    @Test
    void anAttemptHasOneAssignmentPerSession() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID attemptId = UUID.randomUUID();
        assignmentRepository.saveAndFlush(new GradingAssignment(tenantId, sessionId, attemptId, UUID.randomUUID(), 1,
                UUID.randomUUID(), Instant.now()));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> assignmentRepository.saveAndFlush(
                new GradingAssignment(tenantId, sessionId, attemptId, UUID.randomUUID(), 1, UUID.randomUUID(),
                        Instant.now()))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void startingMovesTheOwnProgressWithoutChangingTheOwner() {
        UUID tenantId = UUID.randomUUID();
        UUID examiner = UUID.randomUUID();
        GradingAssignment saved = assignmentRepository.saveAndFlush(assignment(tenantId, UUID.randomUUID(),
                UUID.randomUUID(), examiner, 4));
        assertThat(saved.isStarted()).isFalse();
        assertThat(saved.getStatus()).isEqualTo(ExaminerWorkflowStatus.PENDING);

        saved.start(Instant.parse("2026-10-24T10:00:00Z"));
        assignmentRepository.saveAndFlush(saved);

        GradingAssignment reloaded = assignmentRepository.findForUpdate(tenantId, saved.getPublicId()).orElseThrow();
        assertThat(reloaded.isStarted()).isTrue();
        assertThat(reloaded.getWorkVersion()).isEqualTo(1L);
        assertThat(reloaded.getStatus()).isEqualTo(ExaminerWorkflowStatus.IN_PROGRESS);
        assertThat(reloaded.getExaminerPublicId()).isEqualTo(examiner);
    }

    private GradingAssignment assignment(UUID tenantId, UUID sessionId, UUID hostId, UUID examinerId,
            int answerCount) {
        return new GradingAssignment(tenantId, sessionId, UUID.randomUUID(), examinerId, answerCount, hostId,
                Instant.now());
    }
}
