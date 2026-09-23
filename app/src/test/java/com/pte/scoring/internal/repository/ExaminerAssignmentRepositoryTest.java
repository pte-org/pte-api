package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAssignmentBatch;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.enums.AssignmentBatchMode;
import com.pte.scoring.domain.enums.AssignmentBatchStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ExaminerAssignmentRepositoryTest {

    @Autowired
    private ExaminerAssignmentBatchRepository batchRepository;

    @Autowired
    private ExaminerAttemptAssignmentRepository assignmentRepository;

    @Test
    void batchHistoryCanBePagedAndExpiredPreviewsArePersistentlyMarked() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        ExaminerAssignmentBatch overdue = batch(tenantId, sessionId, now.minusSeconds(1));
        ExaminerAssignmentBatch fresh = batch(tenantId, sessionId, now.plusSeconds(60));
        batchRepository.saveAllAndFlush(List.of(overdue, fresh));

        int expiredCount = batchRepository.expireOverduePreviews(tenantId, sessionId,
                AssignmentBatchStatus.PREVIEWED, AssignmentBatchStatus.EXPIRED, now);
        Page<ExaminerAssignmentBatch> page = batchRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(
                tenantId, sessionId, PageRequest.of(0, 1,
                        Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));

        assertThat(expiredCount).isEqualTo(1);
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(batchRepository.findByPublicIdAndTenantIdAndSessionPublicId(
                overdue.getPublicId(), tenantId, sessionId)).get()
                .extracting(ExaminerAssignmentBatch::getStatus).isEqualTo(AssignmentBatchStatus.EXPIRED);
        assertThat(batchRepository.findByPublicIdAndTenantIdAndSessionPublicId(
                fresh.getPublicId(), tenantId, sessionId)).get()
                .extracting(ExaminerAssignmentBatch::getStatus).isEqualTo(AssignmentBatchStatus.PREVIEWED);
    }

    @Test
    void committedAssignmentLoadsAreAggregatedFromPersistedAnswerCounts() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        UUID examinerA = UUID.randomUUID();
        UUID examinerB = UUID.randomUUID();
        Instant committedAt = Instant.now();
        ExaminerAssignmentBatch batch = batch(tenantId, sessionId, committedAt.plusSeconds(60));
        batch.commit(committedAt);
        batchRepository.saveAndFlush(batch);
        assignmentRepository.saveAllAndFlush(List.of(
                assignment(batch, tenantId, sessionId, hostId, examinerA, 2),
                assignment(batch, tenantId, sessionId, hostId, examinerA, 3),
                assignment(batch, tenantId, sessionId, hostId, examinerB, 5)));

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

    private ExaminerAssignmentBatch batch(UUID tenantId, UUID sessionId, Instant expiresAt) {
        return new ExaminerAssignmentBatch(tenantId, sessionId, UUID.randomUUID(), AssignmentBatchMode.RANDOM,
                "{}", "[]", UUID.randomUUID(), expiresAt);
    }

    private ExaminerAttemptAssignment assignment(ExaminerAssignmentBatch batch, UUID tenantId, UUID sessionId,
            UUID hostId, UUID examinerId, int answerCount) {
        return new ExaminerAttemptAssignment(batch.getPublicId(), tenantId, sessionId, UUID.randomUUID(), examinerId,
                answerCount, hostId, Instant.now());
    }
}
