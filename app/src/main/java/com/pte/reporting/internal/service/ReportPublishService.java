package com.pte.reporting.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptSummaryView;
import com.pte.reporting.domain.ExamResult;
import com.pte.reporting.domain.enums.Skill;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.reporting.internal.dto.response.ReportPublicationBlockerResponse;
import com.pte.reporting.internal.dto.response.ReportPublicationReadinessResponse;
import com.pte.reporting.internal.dto.response.ReportPublicationSummaryResponse;
import com.pte.reporting.internal.exception.ReportPublicationNotReadyException;
import com.pte.reporting.internal.repository.ExamResultRepository;
import com.pte.scoring.ScoringService;
import com.pte.scoring.dto.response.ReportPublicationScoringView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import com.pte.session.SessionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Atomically validates, snapshots, and publishes the full submitted session cohort. */
@Service
public class ReportPublishService {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ScoringService scoringService;
    private final ScoreAggregationService aggregationService;
    private final ReportSnapshotCodec snapshotCodec;
    private final ExamResultRepository examResultRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public ReportPublishService(SessionService sessionService, AttemptService attemptService,
            ScoringService scoringService, ScoreAggregationService aggregationService,
            ReportSnapshotCodec snapshotCodec, ExamResultRepository examResultRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.scoringService = scoringService;
        this.aggregationService = aggregationService;
        this.snapshotCodec = snapshotCodec;
        this.examResultRepository = examResultRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReportPublicationReadinessResponse preflight(UUID sessionPublicId, UUID tenantId) {
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        boolean closed = sessionService.isSessionClosed(sessionPublicId, tenantId);
        List<ReportPublicationBlockerResponse> blockers = new ArrayList<>();
        List<AttemptSummaryView> attempts = resolveCohortAttempts(sessionPublicId, tenantId, blockers);
        List<ReportScoringAnswerView> inputs = scoringService.getReportScoringInputs(tenantId, sessionPublicId);
        return readiness(sessionPublicId, closed, attempts, inputs, blockers);
    }

    @Transactional(readOnly = true)
    public ReportPublicationSummaryResponse publicationSummary(UUID sessionPublicId, UUID tenantId) {
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        return examResultRepository
                .findFirstBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNullOrderByPublishedAtAsc(
                        sessionPublicId, tenantId)
                .map(report -> new ReportPublicationSummaryResponse(sessionPublicId,
                        report.getPublicationPublicId(), report.getPublishedByPublicId(), report.getPublishedAt(),
                        report.getPublicationCohortSize() == null ? 0 : report.getPublicationCohortSize(),
                        examResultRepository.countBySessionPublicIdAndTenantIdAndPublishedTrueAndReportSnapshotJsonIsNotNull(
                                sessionPublicId, tenantId)))
                .orElse(null);
    }

    @Transactional
    public int publishSession(UUID sessionPublicId, UUID tenantId, UUID actorPublicId) {
        sessionService.lockClosedForReportPublication(sessionPublicId, tenantId);
        List<ReportPublicationBlockerResponse> blockers = new ArrayList<>();
        List<AttemptSummaryView> attempts = resolveCohortAttempts(sessionPublicId, tenantId, blockers);

        if (attempts.isEmpty()) {
            if (blockers.isEmpty()) {
                return 0;
            }
            ReportPublicationReadinessResponse readiness =
                    readiness(sessionPublicId, true, attempts, List.of(), blockers);
            throw new ReportPublicationNotReadyException(readiness.blockers());
        }

        ReportPublicationScoringView locked = scoringService.lockForReportPublication(tenantId, sessionPublicId,
                UUID.randomUUID());
        ReportPublicationReadinessResponse readiness = readiness(sessionPublicId, true, attempts, locked.answers(), blockers);
        if (!readiness.canPublish()) {
            throw new ReportPublicationNotReadyException(readiness.blockers());
        }

        Set<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).collect(Collectors.toSet());
        var inputsByAttempt = locked.answers().stream().filter(input -> attemptIds.contains(input.attemptPublicId()))
                .collect(Collectors.groupingBy(ReportScoringAnswerView::attemptPublicId));
        Map<UUID, ReportScoreAggregation> scoreAggregations =
                aggregationService.aggregateBatchFromInputs(attemptIds, inputsByAttempt);
        Instant publishedAt = clock.instant();
        int cohortSize = attempts.size();
        for (AttemptSummaryView summary : attempts) {
            List<ReportScoringAnswerView> attemptInputs =
                    inputsByAttempt.getOrDefault(summary.attemptPublicId(), List.of());
            ExamResult report = findOrCreate(summary);
            boolean newlyPublished = !report.isPublished();
            if (report.isPublished() && report.getReportSnapshotJson() != null) {
                continue;
            }
            ReportScoreAggregation scoreAggregation = scoreAggregations.get(summary.attemptPublicId());
            if (scoreAggregation == null) {
                throw new IllegalStateException(ReportingConstants.SCORE_AGGREGATION_MISSING);
            }
            String snapshotJson = snapshotCodec.encode(locked.publicationPublicId(), actorPublicId, publishedAt,
                cohortSize, summary.snapshotPublicId(), scoreAggregation.context().examDefinitionPublicId(),
                scoreAggregation.context().examDefinitionVersion(), scoreAggregation.summary(), attemptInputs);
            recordScores(report, scoreAggregation.summary());
            report.publishSnapshot(snapshotJson, locked.publicationPublicId(), actorPublicId, cohortSize, publishedAt);
            examResultRepository.save(report);
            if (newlyPublished) {
                eventPublisher.publishEvent(new AttemptPublishedEvent(report.getAttemptPublicId(),
                        report.getSessionPublicId(), report.getStudentPublicId(), report.getTenantId()));
            }
        }
        return cohortSize;
    }

    private static void recordScores(ExamResult report, AttemptScoreSummary summary) {
        report.recordScores(scoreOf(summary.overall()), skillScore(summary, Skill.LISTENING),
                skillScore(summary, Skill.READING), skillScore(summary, Skill.SPEAKING),
                skillScore(summary, Skill.WRITING));
    }

    private static Integer skillScore(AttemptScoreSummary summary, Skill skill) {
        return summary.skillScores() == null ? null : scoreOf(summary.skillScores().get(skill));
    }

    private static Integer scoreOf(SkillScore score) {
        return score == null || !score.sufficientData() ? null : score.score();
    }

    private ReportPublicationReadinessResponse readiness(UUID sessionPublicId, boolean closed,
            List<AttemptSummaryView> attempts, List<ReportScoringAnswerView> inputs) {
        return readiness(sessionPublicId, closed, attempts, inputs, List.of());
    }

    private ReportPublicationReadinessResponse readiness(UUID sessionPublicId, boolean closed,
            List<AttemptSummaryView> attempts, List<ReportScoringAnswerView> inputs,
            List<ReportPublicationBlockerResponse> initialBlockers) {
        Set<UUID> attemptIds = attempts.stream().map(AttemptSummaryView::attemptPublicId).collect(Collectors.toSet());
        List<ReportScoringAnswerView> cohortInputs = inputs.stream()
                .filter(input -> attemptIds.contains(input.attemptPublicId())).toList();
        Map<UUID, List<ReportScoringAnswerView>> inputsByAttempt = cohortInputs.stream()
                .collect(Collectors.groupingBy(ReportScoringAnswerView::attemptPublicId));
        List<ReportPublicationBlockerResponse> blockers = new ArrayList<>(initialBlockers);
        Set<UUID> blockedAttemptIds = new HashSet<>();
        for (ReportPublicationBlockerResponse initialBlocker : initialBlockers) {
            blockedAttemptIds.add(initialBlocker.attemptPublicId());
        }
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
        // Session-level blockers carry the grading id, not an attempt id, so only count the attempts that are blocked.
        int readyAttempts = (int) attemptIds.stream().filter(id -> !blockedAttemptIds.contains(id)).count();
        boolean canPublish = closed && !attemptIds.isEmpty() && blockers.isEmpty();
        return new ReportPublicationReadinessResponse(sessionPublicId, closed, attempts.size(), readyAttempts,
                blockers.size(), canPublish, blockers);
    }

    private List<AttemptSummaryView> resolveCohortAttempts(UUID sessionPublicId, UUID tenantId,
            List<ReportPublicationBlockerResponse> blockers) {
        List<AttemptSummaryView> allSubmitted = attemptService.getSubmittedAttemptsForSession(sessionPublicId, tenantId);
        var cohortOpt = scoringService.getCohortPublicationView(sessionPublicId, tenantId);
        if (cohortOpt.isEmpty()) {
            return allSubmitted;
        }
        var cohort = cohortOpt.get();
        Set<UUID> requiredIds = new HashSet<>(cohort.requiredAttemptPublicIds());
        Set<UUID> excludedIds = new HashSet<>(cohort.excludedAttemptPublicIds());
        Map<UUID, AttemptSummaryView> submittedById = allSubmitted.stream()
                .collect(Collectors.toMap(AttemptSummaryView::attemptPublicId, Function.identity(), (a, b) -> a));

        for (UUID requiredId : requiredIds) {
            if (!submittedById.containsKey(requiredId)) {
                blockers.add(new ReportPublicationBlockerResponse(requiredId, null, null, null,
                        ReportingConstants.REASON_SCORING_INPUTS_MISSING));
            }
        }

        if (cohort.manualExaminer()) {
            scoringService.evaluateManualPublicationBlockers(sessionPublicId, tenantId).ifPresent(reasons -> {
                for (String reason : reasons) {
                    blockers.add(new ReportPublicationBlockerResponse(sessionPublicId, null, null, null, reason));
                }
            });
        }

        return allSubmitted.stream()
                .filter(attempt -> requiredIds.contains(attempt.attemptPublicId()))
                .filter(attempt -> !excludedIds.contains(attempt.attemptPublicId()))
                .toList();
    }

    private ExamResult findOrCreate(AttemptSummaryView summary) {
        return examResultRepository.findByAttemptPublicId(summary.attemptPublicId())
                .orElseGet(() -> save(ReportService.newReport(summary)));
    }

    private ExamResult save(ExamResult report) {
        try {
            return examResultRepository.save(report);
        } catch (DataIntegrityViolationException ex) {
            return examResultRepository.findByAttemptPublicId(report.getAttemptPublicId()).orElseThrow();
        }
    }
}
