package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.AttemptReport;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.dto.response.ReportPublicationBlockerResponse;
import com.pte.reporting.internal.dto.response.ReportPublicationReadinessResponse;
import com.pte.reporting.internal.dto.response.ReportPublicationSummaryResponse;
import com.pte.reporting.internal.exception.ReportPublicationNotReadyException;
import com.pte.reporting.internal.repository.AttemptReportRepository;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.session.SessionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Atomically validates, snapshots, and publishes the full submitted session cohort. */
@Service
public class ReportPublishService {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ScoringService scoringService;
    private final ScoreAggregationService aggregationService;
    private final ReportSnapshotCodec snapshotCodec;
    private final AttemptReportRepository attemptReportRepository;
    private final ApplicationEventPublisher eventPublisher;

    public ReportPublishService(SessionService sessionService, AttemptService attemptService,
            ScoringService scoringService, ScoreAggregationService aggregationService,
            ReportSnapshotCodec snapshotCodec, AttemptReportRepository attemptReportRepository,
            ApplicationEventPublisher eventPublisher) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.scoringService = scoringService;
        this.aggregationService = aggregationService;
        this.snapshotCodec = snapshotCodec;
        this.attemptReportRepository = attemptReportRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public ReportPublicationReadinessResponse preflight(UUID sessionPublicId, UUID tenantId) {
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        boolean closed = sessionService.isSessionClosed(sessionPublicId, tenantId);
        List<AttemptSummaryView> attempts = submittedAttempts(sessionPublicId, tenantId);
        List<ReportScoringAnswerView> inputs = scoringService.getReportScoringInputs(tenantId, sessionPublicId);
        return readiness(sessionPublicId, closed, attempts, inputs);
    }

    @Transactional(readOnly = true)
    public ReportPublicationSummaryResponse publicationSummary(UUID sessionPublicId, UUID tenantId) {
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        return attemptReportRepository
                .findFirstBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNullOrderByPublishedAtAsc(
                        sessionPublicId, tenantId)
                .map(report -> new ReportPublicationSummaryResponse(sessionPublicId,
                        report.getPublicationPublicId(), report.getPublishedByPublicId(), report.getPublishedAt(),
                        report.getPublicationCohortSize() == null ? 0 : report.getPublicationCohortSize(),
                        attemptReportRepository.countBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNull(
                                sessionPublicId, tenantId)))
                .orElse(null);
    }

    @Transactional
    public int publishSession(UUID sessionPublicId, UUID tenantId, UUID actorPublicId) {
        sessionService.lockClosedForReportPublication(sessionPublicId, tenantId);
        List<AttemptSummaryView> attempts = submittedAttempts(sessionPublicId, tenantId);
        if (attempts.isEmpty()) {
            return 0;
        }

        ReportPublicationScoringView locked = scoringService.lockForReportPublication(tenantId, sessionPublicId,
                UUID.randomUUID());
        ReportPublicationReadinessResponse readiness = readiness(sessionPublicId, true, attempts, locked.answers());
        if (!readiness.canPublish()) {
            throw new ReportPublicationNotReadyException(readiness.blockers());
        }

        Set<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).collect(Collectors.toSet());
        var inputsByAttempt = locked.answers().stream().filter(input -> attemptIds.contains(input.attemptPublicId()))
                .collect(Collectors.groupingBy(ReportScoringAnswerView::attemptPublicId));
        Map<UUID, ReportScoreAggregation> scoreAggregations =
                aggregationService.aggregateBatchFromInputs(attemptIds, inputsByAttempt);
        Instant publishedAt = Instant.now();
        int cohortSize = attempts.size();
        for (AttemptSummaryView summary : attempts) {
            List<ReportScoringAnswerView> attemptInputs =
                    inputsByAttempt.getOrDefault(summary.attemptPublicId(), List.of());
            AttemptReport report = findOrCreate(summary);
            boolean newlyPublished = !report.isPublished();
            if (report.isPublished() && report.getReportSnapshotJson() != null) {
                continue;
            }
            ReportScoreAggregation scoreAggregation = scoreAggregations.get(summary.attemptPublicId());
            if (scoreAggregation == null) {
                throw new IllegalStateException(ReportingConstants.SCORE_AGGREGATION_MISSING);
            }
            String snapshotJson = snapshotCodec.encode(locked.publicationPublicId(), actorPublicId, publishedAt,
                    cohortSize, summary.snapshotPublicId(), scoreAggregation.context().scoreTemplatePublicId(),
                    scoreAggregation.context().scoreTemplateVersion(), scoreAggregation.summary(), attemptInputs);
            report.publishSnapshot(snapshotJson, locked.publicationPublicId(), actorPublicId, cohortSize, publishedAt);
            attemptReportRepository.save(report);
            if (newlyPublished) {
                eventPublisher.publishEvent(new AttemptPublishedEvent(report.getAttemptPublicId(),
                        report.getSessionPublicId(), report.getStudentPublicId(), report.getTenantId()));
            }
        }
        return cohortSize;
    }

    private ReportPublicationReadinessResponse readiness(UUID sessionPublicId, boolean closed,
            List<AttemptSummaryView> attempts, List<ReportScoringAnswerView> inputs) {
        Set<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).collect(Collectors.toSet());
        List<ReportScoringAnswerView> cohortInputs = inputs.stream()
                .filter(input -> attemptIds.contains(input.attemptPublicId())).toList();
        Map<UUID, List<ReportScoringAnswerView>> inputsByAttempt = cohortInputs.stream()
                .collect(Collectors.groupingBy(ReportScoringAnswerView::attemptPublicId));
        List<ReportPublicationBlockerResponse> blockers = new ArrayList<>();
        Set<UUID> blockedAttemptIds = new HashSet<>();
        for (UUID attemptId : attemptIds) {
            List<ReportScoringAnswerView> attemptInputs = inputsByAttempt.getOrDefault(attemptId, List.of());
            if (attemptInputs.isEmpty()) {
                blockers.add(new ReportPublicationBlockerResponse(attemptId, null, null, null,
                        ReportingConstants.REASON_SCORING_INPUTS_MISSING));
                blockedAttemptIds.add(attemptId);
            }
            for (ReportScoringAnswerView input : attemptInputs) {
                if (!input.publishable()) {
                    blockers.add(new ReportPublicationBlockerResponse(input.attemptPublicId(), input.answerPublicId(),
                            input.section(), input.taskType(), input.blockingReason()));
                    blockedAttemptIds.add(attemptId);
                }
            }
        }
        blockers.sort(Comparator.comparing(ReportPublicationBlockerResponse::attemptPublicId)
                .thenComparing(blocker -> blocker.answerPublicId() == null ? "" : blocker.answerPublicId().toString()));
        int readyAttempts = attemptIds.size() - blockedAttemptIds.size();
        boolean canPublish = closed && !attemptIds.isEmpty() && blockers.isEmpty();
        return new ReportPublicationReadinessResponse(sessionPublicId, closed, attempts.size(), readyAttempts,
                blockers.size(), canPublish, blockers);
    }

    private List<AttemptSummaryView> submittedAttempts(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId);
    }

    private AttemptReport findOrCreate(AttemptSummaryView summary) {
        return attemptReportRepository.findByAttemptPublicId(summary.attemptPublicId())
                .orElseGet(() -> save(ReportService.newReport(summary)));
    }

    private AttemptReport save(AttemptReport report) {
        try {
            return attemptReportRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            return attemptReportRepository.findByAttemptPublicId(report.getAttemptPublicId()).orElseThrow();
        }
    }
}
