package com.pte.scoring.internal.repository;

import com.pte.scoring.ScoringOverviewService;
import com.pte.scoring.ScoringOverviewService.ScoringBacklog;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.service.SessionGradingGateway;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Grading backlog counts behind {@link ScoringOverviewService}: status mapping, soft delete, tenant isolation. */
@DataJpaTest
@Import(ScoringOverviewService.class)
class ScoringOverviewQueriesTest {

    @Autowired
    private ScoringOverviewService service;

    @Autowired
    private AiEvaluationRepository answers;

    @MockitoBean
    private SessionGradingGateway gateway;

    private void answer(UUID tenant, ScoringAnswerStatus status, boolean deleted) {
        AiEvaluation answer = EvaluationFixtures.ai(tenant, UUID.randomUUID(), "WRITE_ESSAY",
                ScoringMethod.AI_TEXT);
        answer.setStatus(status);
        answer.setDeleted(deleted);
        answers.saveAndFlush(answer);
    }

    private void answers(UUID tenant, ScoringAnswerStatus status, int count) {
        for (int i = 0; i < count; i++) {
            answer(tenant, status, false);
        }
    }

    @Test
    void groupedCountsMapEachScoringStatusToItsOwnBacklogField() {
        UUID tenant = UUID.randomUUID();
        answers(tenant, ScoringAnswerStatus.PENDING, 3);
        answers(tenant, ScoringAnswerStatus.AI_SCORING, 2);
        answers(tenant, ScoringAnswerStatus.SCORING_FAILED, 1);
        answers(tenant, ScoringAnswerStatus.SCORED, 5); // finished work is not backlog

        ScoringBacklog backlog = service.backlog(tenant);

        assertThat(backlog.pendingAnswers()).isEqualTo(3);
        assertThat(backlog.aiScoringAnswers()).isEqualTo(2);
        assertThat(backlog.failedAnswers()).isEqualTo(1);
        assertThat(backlog.closedSessionsAwaitingFinalisation()).isZero();
    }

    @Test
    void aStatusWithNoRowsIsZeroNotMissing() {
        UUID tenant = UUID.randomUUID();
        answers(tenant, ScoringAnswerStatus.PENDING, 2);

        ScoringBacklog backlog = service.backlog(tenant);

        assertThat(backlog).isEqualTo(new ScoringBacklog(2, 0, 0, 0));
    }

    @Test
    void anEmptyTenantIsAllZeros() {
        answers(UUID.randomUUID(), ScoringAnswerStatus.PENDING, 4);

        assertThat(service.backlog(UUID.randomUUID())).isEqualTo(new ScoringBacklog(0, 0, 0, 0));
    }

    @Test
    void softDeletedAnswersAreNotCounted() {
        UUID tenant = UUID.randomUUID();
        answer(tenant, ScoringAnswerStatus.PENDING, false);
        answer(tenant, ScoringAnswerStatus.PENDING, true);
        answer(tenant, ScoringAnswerStatus.AI_SCORING, true);
        answer(tenant, ScoringAnswerStatus.SCORING_FAILED, true);

        assertThat(service.backlog(tenant)).isEqualTo(new ScoringBacklog(1, 0, 0, 0));
    }

    @Test
    void tenantAnswerCountsNeverIncludeAnotherTenant() {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        answers(tenantA, ScoringAnswerStatus.PENDING, 1);
        answers(tenantA, ScoringAnswerStatus.SCORING_FAILED, 2);
        answers(tenantB, ScoringAnswerStatus.PENDING, 40);
        answers(tenantB, ScoringAnswerStatus.AI_SCORING, 30);
        answers(tenantB, ScoringAnswerStatus.SCORING_FAILED, 20);

        assertThat(service.backlog(tenantA)).isEqualTo(new ScoringBacklog(1, 0, 2, 0));
        assertThat(service.backlog(tenantB)).isEqualTo(new ScoringBacklog(40, 30, 20, 0));
    }

    @Test
    void closedSessionsAwaitingFinalisationComesFromTheSessionGradingState() {
        UUID tenant = UUID.randomUUID();
        when(gateway.countFrozen(tenant)).thenReturn(2L);

        assertThat(service.backlog(tenant).closedSessionsAwaitingFinalisation()).isEqualTo(2);
        assertThat(service.backlog(UUID.randomUUID()).closedSessionsAwaitingFinalisation()).isZero();
    }

    @Test
    void answerAndFrozenSessionCountsAreIndependent() {
        UUID tenant = UUID.randomUUID();
        answers(tenant, ScoringAnswerStatus.PENDING, 2);
        when(gateway.countFrozen(tenant)).thenReturn(1L);

        assertThat(service.backlog(tenant)).isEqualTo(new ScoringBacklog(2, 0, 0, 1));
    }
}
