package com.pte.reporting.service;

import com.pte.common.web.ExportPage;
import com.pte.reporting.client.ExamDeliveryExportClient;
import com.pte.reporting.client.ScoringExportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Reporting's read-model rebuild (rabbitmq-outbox-migration Phase 9) — the
 * first-ever from-scratch rebuild capability reporting has had (Kafka replay
 * was never actually wired up, see plan.md's Overview correction). Pages
 * through exam-delivery's attempt/answer export and scoring's answer-scored
 * export from the beginning, applying the SAME upsert logic ({@link
 * AttemptProjectionService}/{@link AnswerScoreService}) the steady-state
 * {@code @RabbitListener}s use.
 *
 * <p><b>Known gap, documented not silent:</b> this rebuild does NOT restore
 * {@code AttemptReport.published}/{@code publishedAt}. That state was never a
 * projection of an external event stream — it's a decision reporting itself
 * makes when it processes scheduling's {@code PublishRequested} command, and
 * scheduling keeps no durable "this session was published" record to export
 * (confirmed: {@code ExamSession} has no such field). A rebuilt attempt is
 * therefore always unpublished; an operator must manually re-trigger publish
 * for any session that needs it visible again after a rebuild.
 *
 * <p>{@code tenantId = null} means "all tenants" (bootstrap mode) — callers
 * must have already resolved and authorized this via {@code
 * com.pte.common.security.InternalExportScope} equivalent checks before
 * reaching here; this class does not re-check authorization, only scope.
 */
@Service
public class RebuildOrchestrationService {

    private static final Logger log = LoggerFactory.getLogger(RebuildOrchestrationService.class);

    private final ExamDeliveryExportClient examDeliveryExportClient;
    private final ScoringExportClient scoringExportClient;
    private final AttemptProjectionService attemptProjectionService;
    private final AnswerScoreService answerScoreService;

    public RebuildOrchestrationService(ExamDeliveryExportClient examDeliveryExportClient,
            ScoringExportClient scoringExportClient, AttemptProjectionService attemptProjectionService,
            AnswerScoreService answerScoreService) {
        this.examDeliveryExportClient = examDeliveryExportClient;
        this.scoringExportClient = scoringExportClient;
        this.attemptProjectionService = attemptProjectionService;
        this.answerScoreService = answerScoreService;
    }

    public RebuildSummary rebuild(UUID tenantId) {
        int attempts = pageThrough(examDeliveryExportClient::exportAttempts, attemptProjectionService::ingestAttempt, tenantId);
        int answers = pageThrough(examDeliveryExportClient::exportAnswers, attemptProjectionService::ingestAnswer, tenantId);
        int scored = pageThrough(scoringExportClient::exportAnswersScored, answerScoreService::applyScore, tenantId);
        log.info("Rebuild complete for tenant={}: {} attempts, {} answers, {} scores applied",
                tenantId == null ? "ALL" : tenantId, attempts, answers, scored);
        return new RebuildSummary(attempts, answers, scored);
    }

    private <T> int pageThrough(BiFunction<UUID, String, ExportPage<T>> fetch, Consumer<T> apply, UUID tenantId) {
        int count = 0;
        String cursor = null;
        boolean hasMore = true;
        while (hasMore) {
            ExportPage<T> page = fetch.apply(tenantId, cursor);
            page.items().forEach(apply);
            count += page.items().size();
            cursor = page.nextCursor();
            hasMore = page.hasMore() && cursor != null;
        }
        return count;
    }

    public record RebuildSummary(int attemptsIngested, int answersIngested, int scoresApplied) {
    }
}
