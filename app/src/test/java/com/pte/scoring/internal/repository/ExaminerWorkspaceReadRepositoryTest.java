package com.pte.scoring.internal.repository;

import com.pte.scoring.dto.response.ExaminerQueueItemResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "logging.level.root=ERROR",
        "logging.level.org.hibernate.SQL=OFF",
        "spring.jpa.show-sql=false"
})
@Import(ExaminerWorkspaceReadRepository.class)
class ExaminerWorkspaceReadRepositoryTest {

    @Autowired
    private ExaminerWorkspaceReadRepository readRepository;

    @ParameterizedTest
    @ValueSource(strings = {"ALL", "PENDING", "IN_PROGRESS", "COMPLETED"})
    void testFindQueueEmptyReturnsZeroForEveryStatus(String status) {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();

        Page<ExaminerQueueItemResponse> resultAsc = readRepository.findQueue(
                tenantId, examinerId, null, status, null, "ASSIGNED_ASC", PageRequest.of(0, 20));

        assertThat(resultAsc.getTotalElements()).isZero();
        assertThat(resultAsc.getContent()).isEmpty();

        Page<ExaminerQueueItemResponse> resultDesc = readRepository.findQueue(
                tenantId, examinerId, null, status, null, "ASSIGNED_DESC", PageRequest.of(0, 20));

        assertThat(resultDesc.getTotalElements()).isZero();
        assertThat(resultDesc.getContent()).isEmpty();
    }

    @Test
    void testFindQueueWithSearchAndSession() {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Page<ExaminerQueueItemResponse> result = readRepository.findQueue(
                tenantId, examinerId, sessionId, "IN_PROGRESS", "some-search", "ASSIGNED_ASC", PageRequest.of(0, 20));

        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    void testGetReportsWhenEmptyReturnsZeroSummaryAndEmptyLists() {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();

        com.pte.scoring.dto.response.ExaminerReportsResponse reports = readRepository.getReports(
                tenantId, examinerId, null, null, null, null, null);

        assertThat(reports).isNotNull();
        assertThat(reports.summary()).isNotNull();
        assertThat(reports.summary().finalizedCount()).isZero();
        assertThat(reports.summary().inProgressCount()).isZero();
        assertThat(reports.summary().pendingCount()).isZero();
        assertThat(reports.summary().totalAssignedCount()).isZero();
        assertThat(reports.summary().avgGradingDurationMinutes()).isZero();
        assertThat(reports.summary().regradeCount()).isZero();
        assertThat(reports.taskTypeBreakdowns()).isEmpty();
        assertThat(reports.recentFinalizedItems()).isEmpty();
    }

    @Test
    void testGetReportsWithFilters() {
        UUID tenantId = UUID.randomUUID();
        UUID examinerId = UUID.randomUUID();
        java.time.Instant from = java.time.Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS);
        java.time.Instant to = java.time.Instant.now();

        com.pte.scoring.dto.response.ExaminerReportsResponse reports = readRepository.getReports(
                tenantId, examinerId, from, to, "READ_ALOUD", "COMPLETED", UUID.randomUUID());

        assertThat(reports).isNotNull();
        assertThat(reports.summary().totalAssignedCount()).isZero();
        assertThat(reports.taskTypeBreakdowns()).isEmpty();
        assertThat(reports.recentFinalizedItems()).isEmpty();
    }
}
