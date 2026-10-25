package com.pte.scoring.internal.service.grading;

import com.pte.scoring.internal.service.ScoringIngestService;
import com.pte.scoring.internal.service.selection.ScoreSelectionReader;
import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.FinalizeGradingCohortRequest;
import com.pte.scoring.dto.response.GradingCohortAttemptResponse;
import com.pte.scoring.dto.response.GradingCohortPreviewResponse;
import com.pte.scoring.dto.response.GradingCohortResponse;
import com.pte.scoring.dto.response.SessionCohortPublicationView;
import com.pte.scoring.internal.constant.GradingConstants;
import com.pte.scoring.internal.exception.GradingCohortException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.service.grading.SessionGradingGateway.Grading;
import com.pte.session.SessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Freezes the grading of a CLOSED session and reconciles it until every required item has its score. The state is
 * a field of the session; the people graded are the session's attempts, each either required or left out (excluded)
 * with a reason. Callers hold the session row lock, so no lock of its own is taken here.
 */
@Service
public class GradingCohortService {

    private static final Logger log = LoggerFactory.getLogger(GradingCohortService.class);
    /** The cohort used to be its own row; its id is now the session's. */
    private static final long GRADING_VERSION = 1L;

    private final SessionService sessionService;
    private final SessionGradingGateway gateway;
    private final AttemptService attemptService;
    private final ScoringIngestService ingestService;
    private final AiEvaluationRepository answerRepository;
    private final GradingAssignmentRepository assignmentRepository;
    private final ExaminerEvaluationRepository examinerScoreRepository;
    private final GradingCompletionEvaluator evaluator;
    private final ScoreSelectionReader selectionReader;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private GradingReconciliationWorker reconciliationWorker;

    public GradingCohortService(SessionService sessionService, SessionGradingGateway gateway,
            AttemptService attemptService, ScoringIngestService ingestService, AiEvaluationRepository answerRepository,
            GradingAssignmentRepository assignmentRepository, ExaminerEvaluationRepository examinerScoreRepository,
            GradingCompletionEvaluator evaluator, ScoreSelectionReader selectionReader,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sessionService = sessionService;
        this.gateway = gateway;
        this.attemptService = attemptService;
        this.ingestService = ingestService;
        this.answerRepository = answerRepository;
        this.assignmentRepository = assignmentRepository;
        this.examinerScoreRepository = examinerScoreRepository;
        this.evaluator = evaluator;
        this.selectionReader = selectionReader;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public GradingCohortPreviewResponse preview(UUID sessionPublicId, UUID tenantId) {
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        if (!sessionService.isSessionClosed(sessionPublicId, tenantId)) {
            throw error(HttpStatus.CONFLICT, GradingConstants.SESSION_NOT_CLOSED,
                    GradingConstants.SESSION_NOT_CLOSED_MESSAGE);
        }
        List<AttemptGradingCandidateView> candidates = currentCandidates(sessionPublicId, tenantId);
        Optional<Grading> frozen = gateway.grading(tenantId, sessionPublicId);
        if (frozen.isPresent()) {
            return toPreview(frozen.get(), candidates, List.of());
        }
        String previewVersion = GradingCohortFreezeRules.previewVersion(sessionPublicId, candidates);
        return new GradingCohortPreviewResponse(sessionPublicId, previewVersion, false, null, null,
                (int) candidates.stream().filter(AttemptGradingCandidateView::submitted).count(),
                (int) candidates.stream().filter(candidate -> !candidate.submitted()).count(),
                candidates.stream().map(candidate -> new GradingCohortAttemptResponse(candidate.attemptPublicId(),
                        candidate.studentPublicId(), candidate.status(), false, null)).toList(),
                candidates.isEmpty() ? List.of(GradingConstants.NO_PAPERS) : List.of());
    }

    @Transactional
    public GradingCohortResponse finalizeCohort(UUID sessionPublicId, UUID tenantId, UUID actorPublicId,
            FinalizeGradingCohortRequest request) {
        sessionService.lockClosedForGradingCohort(sessionPublicId, tenantId);
        if (request == null || request.markingMode() == null || request.expectedPreviewVersion() == null
                || request.expectedPreviewVersion().isBlank()) {
            throw error(HttpStatus.BAD_REQUEST, GradingConstants.INVALID_REQUEST,
                    GradingConstants.INVALID_REQUEST_MESSAGE);
        }
        List<AttemptGradingCandidateView> candidates = currentCandidates(sessionPublicId, tenantId);
        String currentVersion = GradingCohortFreezeRules.previewVersion(sessionPublicId, candidates);
        Optional<Grading> existing = gateway.grading(tenantId, sessionPublicId);
        if (existing.isPresent()) {
            Grading grading = existing.get();
            if (!Objects.equals(grading.previewVersion(), request.expectedPreviewVersion())
                    || grading.markingMode() != request.markingMode()
                    || !Objects.equals(grading.previewVersion(), currentVersion)) {
                throw error(HttpStatus.CONFLICT, GradingConstants.COHORT_ALREADY_FINALIZED,
                        GradingConstants.COHORT_ALREADY_FINALIZED_MESSAGE);
            }
            return reconcileLocked(grading, candidates);
        }

        if (!currentVersion.equals(request.expectedPreviewVersion())) {
            throw error(HttpStatus.CONFLICT, GradingConstants.PREVIEW_STALE, GradingConstants.PREVIEW_STALE_MESSAGE);
        }
        if (candidates.stream().noneMatch(AttemptGradingCandidateView::submitted)) {
            throw error(HttpStatus.CONFLICT, GradingConstants.NO_PAPERS, GradingConstants.NO_PAPERS_MESSAGE);
        }
        if (request.markingMode() == GradingMarkingMode.AI_ONLY
                && assignmentRepository.countByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId) > 0) {
            throw error(HttpStatus.CONFLICT, GradingConstants.MARKING_MODE_CONFLICT,
                    GradingConstants.MARKING_MODE_CONFLICT_MESSAGE);
        }
        Map<UUID, String> dispositions = GradingCohortFreezeRules.validateDispositions(candidates,
                request.outstandingDispositions());
        Grading grading = freeze(tenantId, sessionPublicId, actorPublicId, currentVersion, request.markingMode(),
                dispositions);
        return reconcileLocked(grading, currentCandidates(sessionPublicId, tenantId));
    }

    /**
     * Freezes the session's manual-marking grading when the Host commits examiner assignments, so the Host has a
     * single step. Attempts that were never submitted are excluded with an automatic reason. No-op when the
     * grading is already frozen.
     */
    @Transactional
    public void ensureManualCohort(UUID sessionPublicId, UUID tenantId, UUID actorPublicId) {
        sessionService.lockClosedForGradingCohort(sessionPublicId, tenantId);
        if (gateway.grading(tenantId, sessionPublicId).isPresent()) {
            return;
        }
        List<AttemptGradingCandidateView> candidates = currentCandidates(sessionPublicId, tenantId);
        if (candidates.stream().noneMatch(AttemptGradingCandidateView::submitted)) {
            throw error(HttpStatus.CONFLICT, GradingConstants.NO_PAPERS, GradingConstants.NO_PAPERS_MESSAGE);
        }
        Map<UUID, String> dispositions = candidates.stream()
                .filter(candidate -> !candidate.submitted())
                .collect(Collectors.toMap(AttemptGradingCandidateView::attemptPublicId,
                        candidate -> GradingConstants.AUTO_DISPOSITION_NOT_SUBMITTED));
        Grading grading = freeze(tenantId, sessionPublicId, actorPublicId,
                GradingCohortFreezeRules.previewVersion(sessionPublicId, candidates),
                GradingMarkingMode.MANUAL_EXAMINER, dispositions);
        reconcileLocked(grading, currentCandidates(sessionPublicId, tenantId));
    }

    private Grading freeze(UUID tenantId, UUID sessionPublicId, UUID actorPublicId, String previewVersion,
            GradingMarkingMode markingMode, Map<UUID, String> dispositions) {
        Grading grading = gateway.freeze(tenantId, sessionPublicId, markingMode, previewVersion, actorPublicId,
                clock.instant());
        attemptService.excludeFromGrading(sessionPublicId, tenantId, dispositions);
        return grading;
    }

    @Transactional
    public GradingCohortResponse reconcile(UUID sessionPublicId, UUID tenantId) {
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        return gateway.grading(tenantId, sessionPublicId)
                .map(grading -> reconcileLocked(grading, currentCandidates(sessionPublicId, tenantId)))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, GradingConstants.COHORT_ALREADY_FINALIZED,
                        GradingConstants.COHORT_ALREADY_FINALIZED_MESSAGE));
    }

    @Transactional
    public void reconcileIfPresent(UUID sessionPublicId, UUID tenantId) {
        gateway.grading(tenantId, sessionPublicId)
                .ifPresent(grading -> reconcileLocked(grading, currentCandidates(sessionPublicId, tenantId)));
    }

    @Scheduled(fixedDelayString = "${scoring.grading-reconciliation.poll-delay-ms:60000}")
    public void reconcileScheduled() {
        for (Grading grading : gateway.frozen()) {
            if (reconciliationWorker != null) {
                try {
                    reconciliationWorker.reconcileSession(grading.sessionPublicId(), grading.tenantId());
                } catch (Exception ex) {
                    log.warn("Scheduled grading reconciliation failed for session {} (tenantId={}): {}",
                            grading.sessionPublicId(), grading.tenantId(), ex.getMessage());
                }
            } else {
                reconcileIfPresent(grading.sessionPublicId(), grading.tenantId());
            }
        }
    }

    private GradingCohortResponse reconcileLocked(Grading grading, List<AttemptGradingCandidateView> members) {
        UUID tenantId = grading.tenantId();
        UUID sessionPublicId = grading.sessionPublicId();
        ingestService.ingestForSession(sessionPublicId, tenantId);
        GradingEvaluation evaluation = evaluate(grading, members);
        GradingCohortStatus status = grading.status();
        if (evaluation.complete() && gateway.complete(tenantId, sessionPublicId, clock.instant())) {
            status = GradingCohortStatus.COMPLETED;
            eventPublisher.publishEvent(new SessionGradingCompletedEvent(tenantId, sessionPublicId, sessionPublicId,
                    GRADING_VERSION, evaluation.requiredAttemptCount()));
        }
        return response(grading, status, evaluation);
    }

    private GradingEvaluation evaluate(Grading grading, List<AttemptGradingCandidateView> members) {
        UUID tenantId = grading.tenantId();
        UUID sessionPublicId = grading.sessionPublicId();
        List<UUID> requiredAttemptIds = members.stream().filter(member -> !member.excluded())
                .map(AttemptGradingCandidateView::attemptPublicId).toList();
        Map<UUID, AttemptGradingCoverageView> coverage = attemptService.getGradingCoverage(requiredAttemptIds,
                sessionPublicId, tenantId);
        List<AiEvaluation> answers = answerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, tenantId, requiredAttemptIds)
                .stream().filter(answer -> !answer.isDeleted()).toList();
        List<GradingAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(tenantId, sessionPublicId, requiredAttemptIds)
                .stream().filter(assignment -> !assignment.isDeleted()).toList();
        List<ExaminerEvaluation> scores = examinerScoreRepository.findSubmittedBySession(tenantId, sessionPublicId)
                .stream().filter(score -> requiredAttemptIds.contains(score.getAttemptPublicId())).toList();
        return evaluator.evaluate(grading.markingMode(), members, coverage, answers, assignments, scores,
                selectionReader.forSession(sessionPublicId, tenantId));
    }

    private List<AttemptGradingCandidateView> currentCandidates(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getGradingCandidatesForSession(sessionPublicId, tenantId).stream()
                .sorted(Comparator.comparing(AttemptGradingCandidateView::attemptPublicId)).toList();
    }

    private GradingCohortPreviewResponse toPreview(Grading grading, List<AttemptGradingCandidateView> members,
            List<String> reasons) {
        return new GradingCohortPreviewResponse(grading.sessionPublicId(), grading.previewVersion(), true,
                grading.sessionPublicId(), grading.markingMode().name(),
                (int) members.stream().filter(member -> !member.excluded()).count(),
                (int) members.stream().filter(AttemptGradingCandidateView::excluded).count(),
                members.stream().map(member -> new GradingCohortAttemptResponse(member.attemptPublicId(),
                        member.studentPublicId(), member.status(), member.excluded(), member.exclusionReason()))
                        .toList(), reasons);
    }

    private GradingCohortResponse response(Grading grading, GradingCohortStatus status, GradingEvaluation evaluation) {
        return new GradingCohortResponse(grading.sessionPublicId(), grading.sessionPublicId(), GRADING_VERSION,
                status.name(), grading.markingMode().name(), evaluation.requiredAttemptCount(),
                evaluation.excludedAttemptCount(), evaluation.expectedItemCount(), evaluation.satisfiedItemCount(),
                status == GradingCohortStatus.COMPLETED, evaluation.blockingReasons());
    }

    private GradingCohortException error(HttpStatus status, String code, String message) {
        return GradingCohortFreezeRules.error(status, code, message);
    }

    @Transactional(readOnly = true)
    public Optional<SessionCohortPublicationView> getCohortPublicationView(UUID sessionPublicId, UUID tenantId) {
        return gateway.grading(tenantId, sessionPublicId).map(grading -> {
            List<AttemptGradingCandidateView> members = currentCandidates(sessionPublicId, tenantId);
            return new SessionCohortPublicationView(grading.sessionPublicId(), grading.markingMode().name(),
                    grading.status().name(),
                    members.stream().filter(member -> !member.excluded())
                            .map(AttemptGradingCandidateView::attemptPublicId).toList(),
                    members.stream().filter(AttemptGradingCandidateView::excluded)
                            .map(AttemptGradingCandidateView::attemptPublicId).toList());
        });
    }

    @Transactional(readOnly = true)
    public Optional<List<String>> evaluateManualPublicationBlockers(UUID sessionPublicId, UUID tenantId) {
        Optional<Grading> existing = gateway.grading(tenantId, sessionPublicId);
        if (existing.isEmpty() || existing.get().markingMode() != GradingMarkingMode.MANUAL_EXAMINER) {
            return Optional.empty();
        }
        GradingEvaluation evaluation = evaluate(existing.get(), currentCandidates(sessionPublicId, tenantId));
        return Optional.of(evaluation.complete() ? List.of() : evaluation.blockingReasons());
    }
}
