package com.pte.reporting.service;

import com.pte.common.web.ExportPage;
import com.pte.reporting.client.ExamDeliveryExportClient;
import com.pte.reporting.client.ScoringExportClient;
import com.pte.reporting.messaging.consumer.dto.AnswerScoredEvent;
import com.pte.reporting.messaging.consumer.dto.AnswerSubmittedEvent;
import com.pte.reporting.messaging.consumer.dto.AttemptSubmittedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Coverage for {@link RebuildOrchestrationService}'s hand-rolled keyset
 * pagination loop ({@code pageThrough}) — new orchestration logic with no
 * other test.
 */
@ExtendWith(MockitoExtension.class)
class RebuildOrchestrationServiceTest {

    @Mock
    private ExamDeliveryExportClient examDeliveryExportClient;
    @Mock
    private ScoringExportClient scoringExportClient;
    @Mock
    private AttemptProjectionService attemptProjectionService;
    @Mock
    private AnswerScoreService answerScoreService;

    private RebuildOrchestrationService service;

    @BeforeEach
    void setUp() {
        service = new RebuildOrchestrationService(examDeliveryExportClient, scoringExportClient,
                attemptProjectionService, answerScoreService);
        // Default: every export returns an immediately-empty, exhausted page unless overridden per-test.
        when(examDeliveryExportClient.exportAttempts(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExportPage<>(List.of(), null, false));
        when(examDeliveryExportClient.exportAnswers(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExportPage<>(List.of(), null, false));
        when(scoringExportClient.exportAnswersScored(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new ExportPage<>(List.of(), null, false));
    }

    private AttemptSubmittedEvent attemptEvent() {
        return new AttemptSubmittedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    @Test
    void pageThrough_followsNextCursorAcrossMultiplePages_andAppliesEveryItem() {
        UUID tenantId = UUID.randomUUID();
        AttemptSubmittedEvent a1 = attemptEvent();
        AttemptSubmittedEvent a2 = attemptEvent();
        AttemptSubmittedEvent a3 = attemptEvent();
        ExportPage<AttemptSubmittedEvent> page1 = new ExportPage<>(List.of(a1, a2), "cursor-1", true);
        ExportPage<AttemptSubmittedEvent> page2 = new ExportPage<>(List.of(a3), null, false);
        when(examDeliveryExportClient.exportAttempts(tenantId, null)).thenReturn(page1);
        when(examDeliveryExportClient.exportAttempts(tenantId, "cursor-1")).thenReturn(page2);

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(tenantId);

        assertThat(summary.attemptsIngested()).isEqualTo(3);
        verify(examDeliveryExportClient, times(1)).exportAttempts(tenantId, null);
        verify(examDeliveryExportClient, times(1)).exportAttempts(tenantId, "cursor-1");
        InOrder order = inOrder(attemptProjectionService);
        order.verify(attemptProjectionService).ingestAttempt(a1);
        order.verify(attemptProjectionService).ingestAttempt(a2);
        order.verify(attemptProjectionService).ingestAttempt(a3);
    }

    @Test
    void pageThrough_stopsWhenHasMoreFalse_evenIfNextCursorNonNull() {
        UUID tenantId = UUID.randomUUID();
        AttemptSubmittedEvent a1 = attemptEvent();
        // hasMore=false but nextCursor is (inconsistently) non-null: loop must still stop after one page.
        ExportPage<AttemptSubmittedEvent> onlyPage = new ExportPage<>(List.of(a1), "should-be-unused-cursor", false);
        when(examDeliveryExportClient.exportAttempts(tenantId, null)).thenReturn(onlyPage);

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(tenantId);

        assertThat(summary.attemptsIngested()).isEqualTo(1);
        verify(examDeliveryExportClient, times(1)).exportAttempts(tenantId, null);
        verify(examDeliveryExportClient, never()).exportAttempts(tenantId, "should-be-unused-cursor");
        verify(attemptProjectionService).ingestAttempt(a1);
    }

    @Test
    void pageThrough_stopsWhenNextCursorNull_evenIfHasMoreTrue() {
        UUID tenantId = UUID.randomUUID();
        AttemptSubmittedEvent a1 = attemptEvent();
        // hasMore=true but nextCursor is null: loop must still stop (can't page further without a cursor).
        ExportPage<AttemptSubmittedEvent> onlyPage = new ExportPage<>(List.of(a1), null, true);
        when(examDeliveryExportClient.exportAttempts(tenantId, null)).thenReturn(onlyPage);

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(tenantId);

        assertThat(summary.attemptsIngested()).isEqualTo(1);
        verify(examDeliveryExportClient, times(1)).exportAttempts(tenantId, null);
        verify(attemptProjectionService).ingestAttempt(a1);
    }

    @Test
    void rebuild_pagesThroughAllThreeExportsIndependently_andSummarizesCounts() {
        UUID tenantId = UUID.randomUUID();
        AttemptSubmittedEvent attempt = attemptEvent();
        AnswerSubmittedEvent answer = new AnswerSubmittedEvent(UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "SPEAKING_READ_ALOUD");
        AnswerScoredEvent scored = new AnswerScoredEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 79);

        when(examDeliveryExportClient.exportAttempts(tenantId, null))
                .thenReturn(new ExportPage<>(List.of(attempt), null, false));
        when(examDeliveryExportClient.exportAnswers(tenantId, null))
                .thenReturn(new ExportPage<>(List.of(answer), null, false));
        when(scoringExportClient.exportAnswersScored(tenantId, null))
                .thenReturn(new ExportPage<>(List.of(scored), null, false));

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(tenantId);

        assertThat(summary.attemptsIngested()).isEqualTo(1);
        assertThat(summary.answersIngested()).isEqualTo(1);
        assertThat(summary.scoresApplied()).isEqualTo(1);
        verify(attemptProjectionService).ingestAttempt(attempt);
        verify(attemptProjectionService).ingestAnswer(answer);
        verify(answerScoreService).applyScore(scored);
    }

    @Test
    void rebuild_withNullTenantId_bootstrapMode_forwardsNullTenantIdToEveryExportCall() {
        when(examDeliveryExportClient.exportAttempts(null, null))
                .thenReturn(new ExportPage<>(List.of(), null, false));
        when(examDeliveryExportClient.exportAnswers(null, null))
                .thenReturn(new ExportPage<>(List.of(), null, false));
        when(scoringExportClient.exportAnswersScored(null, null))
                .thenReturn(new ExportPage<>(List.of(), null, false));

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(null);

        assertThat(summary.attemptsIngested()).isZero();
        assertThat(summary.answersIngested()).isZero();
        assertThat(summary.scoresApplied()).isZero();
        verify(examDeliveryExportClient).exportAttempts(null, null);
        verify(examDeliveryExportClient).exportAnswers(null, null);
        verify(scoringExportClient).exportAnswersScored(null, null);
    }

    @Test
    void emptyFirstPage_ingestsNothing_andDoesNotLoopFurther() {
        UUID tenantId = UUID.randomUUID();
        when(examDeliveryExportClient.exportAttempts(tenantId, null))
                .thenReturn(new ExportPage<>(List.of(), null, false));

        RebuildOrchestrationService.RebuildSummary summary = service.rebuild(tenantId);

        assertThat(summary.attemptsIngested()).isZero();
        verify(attemptProjectionService, never()).ingestAttempt(org.mockito.ArgumentMatchers.any());
    }
}
