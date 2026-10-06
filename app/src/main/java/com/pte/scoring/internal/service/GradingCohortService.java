package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.AttemptGradingCoverageView;
import com.pte.scoring.SessionGradingCompletedEvent;
import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.request.FinalizeGradingCohortRequest;
import com.pte.scoring.dto.request.GradingCohortDispositionRequest;
import com.pte.scoring.dto.response.GradingCohortAttemptResponse;
import com.pte.scoring.dto.response.GradingCohortPreviewResponse;
import com.pte.scoring.dto.response.GradingCohortResponse;
import com.pte.scoring.internal.constant.GradingConstants;
import com.pte.scoring.internal.exception.GradingCohortException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.GradingCohortMemberRepository;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.session.SessionService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Freezes a CLOSED session's grading cohort and reconciles its scoring barrier. */
@Service
public class GradingCohortService {

    private final SessionService sessionService;
    private final AttemptService attemptService;
    private final ScoringIngestService ingestService;
    private final ScoringAnswerRepository answerRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ExaminerAnswerScoreRepository examinerScoreRepository;
    private final GradingCohortRepository cohortRepository;
    private final GradingCohortMemberRepository memberRepository;
    private final GradingCompletionEvaluator evaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public GradingCohortService(SessionService sessionService, AttemptService attemptService,
            ScoringIngestService ingestService, ScoringAnswerRepository answerRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ExaminerAnswerScoreRepository examinerScoreRepository, GradingCohortRepository cohortRepository,
            GradingCohortMemberRepository memberRepository, GradingCompletionEvaluator evaluator,
            ApplicationEventPublisher eventPublisher, Clock clock) {
        this.sessionService = sessionService;
        this.attemptService = attemptService;
        this.ingestService = ingestService;
        this.answerRepository = answerRepository;
        this.assignmentRepository = assignmentRepository;
        this.examinerScoreRepository = examinerScoreRepository;
        this.cohortRepository = cohortRepository;
        this.memberRepository = memberRepository;
        this.evaluator = evaluator;
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
        var existing = cohortRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(tenantId, sessionPublicId);
        if (existing.isPresent()) {
            List<GradingCohortMember> members = memberRepository
                    .findByCohortPublicIdAndDeletedFalse(existing.get().getPublicId());
            return toPreview(sessionPublicId, existing.get().getPreviewVersion(), existing.get(), members, List.of());
        }
        List<AttemptGradingCandidateView> candidates = currentCandidates(sessionPublicId, tenantId);
        String previewVersion = previewVersion(sessionPublicId, candidates);
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
        var existing = cohortRepository.findForUpdate(tenantId, sessionPublicId);
        if (existing.isPresent()) {
            GradingCohort cohort = existing.get();
            if (!Objects.equals(cohort.getPreviewVersion(), request.expectedPreviewVersion())
                    || cohort.getMarkingMode() != request.markingMode()) {
                throw error(HttpStatus.CONFLICT, GradingConstants.COHORT_ALREADY_FINALIZED,
                        GradingConstants.COHORT_ALREADY_FINALIZED_MESSAGE);
            }
            List<AttemptGradingCandidateView> currentCandidates = currentCandidates(sessionPublicId, tenantId);
            if (!Objects.equals(cohort.getPreviewVersion(), previewVersion(sessionPublicId, currentCandidates))) {
                throw error(HttpStatus.CONFLICT, GradingConstants.COHORT_ALREADY_FINALIZED,
                        GradingConstants.COHORT_ALREADY_FINALIZED_MESSAGE);
            }
            return reconcileLocked(cohort, tenantId, sessionPublicId);
        }

        List<AttemptGradingCandidateView> candidates = currentCandidates(sessionPublicId, tenantId);
        String currentVersion = previewVersion(sessionPublicId, candidates);
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
        Map<UUID, String> dispositions = validateDispositions(candidates, request.outstandingDispositions());
        Instant now = clock.instant();
        GradingCohort cohort = cohortRepository.saveAndFlush(new GradingCohort(tenantId, sessionPublicId,
                actorPublicId, currentVersion, request.markingMode(), now));
        List<GradingCohortMember> members = candidates.stream()
                .map(candidate -> new GradingCohortMember(cohort.getPublicId(), tenantId, sessionPublicId,
                        candidate.attemptPublicId(), candidate.studentPublicId(), candidate.status(),
                        !candidate.submitted(), dispositions.get(candidate.attemptPublicId())))
                .toList();
        memberRepository.saveAll(members);
        memberRepository.flush();
        return reconcileLocked(cohort, tenantId, sessionPublicId);
    }

    @Transactional
    public GradingCohortResponse reconcile(UUID sessionPublicId, UUID tenantId) {
        return cohortRepository.findForUpdate(tenantId, sessionPublicId)
                .map(cohort -> reconcileLocked(cohort, tenantId, sessionPublicId))
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, GradingConstants.COHORT_ALREADY_FINALIZED,
                        GradingConstants.COHORT_ALREADY_FINALIZED_MESSAGE));
    }

    @Transactional
    public void reconcileIfPresent(UUID sessionPublicId, UUID tenantId) {
        cohortRepository.findForUpdate(tenantId, sessionPublicId)
                .ifPresent(cohort -> reconcileLocked(cohort, tenantId, sessionPublicId));
    }

    @Scheduled(fixedDelayString = "${scoring.grading-reconciliation.poll-delay-ms:60000}")
    @Transactional
    public void reconcileScheduled() {
        for (GradingCohort cohort : cohortRepository.findByStatusAndDeletedFalse(GradingCohortStatus.FROZEN)) {
            cohortRepository.findForUpdate(cohort.getTenantId(), cohort.getSessionPublicId())
                    .ifPresent(locked -> reconcileLocked(locked, locked.getTenantId(), locked.getSessionPublicId()));
        }
    }

    private GradingCohortResponse reconcileLocked(GradingCohort cohort, UUID tenantId, UUID sessionPublicId) {
        ingestService.ingestForSession(sessionPublicId, tenantId);
        List<GradingCohortMember> members = memberRepository.findForUpdate(cohort.getPublicId());
        List<UUID> requiredAttemptIds = members.stream().filter(member -> !member.isExcluded())
                .map(GradingCohortMember::getAttemptPublicId).toList();
        Map<UUID, AttemptGradingCoverageView> coverage = attemptService.getGradingCoverage(requiredAttemptIds,
                sessionPublicId, tenantId);
        List<com.pte.scoring.domain.ScoringAnswer> answers = answerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, tenantId, requiredAttemptIds).stream()
                .filter(answer -> !answer.isDeleted()).toList();
        List<com.pte.scoring.domain.ExaminerAttemptAssignment> assignments = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(tenantId, sessionPublicId, requiredAttemptIds).stream()
                .filter(assignment -> !assignment.isDeleted()).toList();
        List<com.pte.scoring.domain.ExaminerAnswerScore> scores = examinerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .filter(score -> !score.isDeleted() && requiredAttemptIds.contains(score.getAttemptPublicId())).toList();
        GradingEvaluation evaluation = evaluator.evaluate(cohort.getMarkingMode(), members, coverage, answers,
                assignments, scores);
        if (evaluation.complete() && cohort.complete(clock.instant())) {
            cohortRepository.saveAndFlush(cohort);
            eventPublisher.publishEvent(new SessionGradingCompletedEvent(tenantId, sessionPublicId,
                    cohort.getPublicId(), cohort.getCohortVersion(), evaluation.requiredAttemptCount()));
        }
        return response(cohort, evaluation);
    }

    private List<AttemptGradingCandidateView> currentCandidates(UUID sessionPublicId, UUID tenantId) {
        return attemptService.getGradingCandidatesForSession(sessionPublicId, tenantId).stream()
                .sorted(Comparator.comparing(AttemptGradingCandidateView::attemptPublicId)).toList();
    }

    private Map<UUID, String> validateDispositions(List<AttemptGradingCandidateView> candidates,
            List<GradingCohortDispositionRequest> requested) {
        Map<UUID, String> dispositions = new LinkedHashMap<>();
        if (requested != null) {
            for (GradingCohortDispositionRequest disposition : requested) {
                if (disposition == null || disposition.attemptPublicId() == null
                        || disposition.reason() == null || disposition.reason().isBlank()
                        || disposition.reason().trim().length() > GradingConstants.DISPOSITION_REASON_LIMIT
                        || dispositions.putIfAbsent(disposition.attemptPublicId(), disposition.reason().trim()) != null) {
                    throw error(HttpStatus.BAD_REQUEST, GradingConstants.INVALID_DISPOSITION,
                            GradingConstants.INVALID_DISPOSITION_MESSAGE);
                }
            }
        }
        Map<UUID, AttemptGradingCandidateView> byId = candidates.stream().collect(Collectors.toMap(
                AttemptGradingCandidateView::attemptPublicId, Function.identity()));
        for (UUID attemptId : dispositions.keySet()) {
            AttemptGradingCandidateView candidate = byId.get(attemptId);
            if (candidate == null || candidate.submitted()) {
                throw error(HttpStatus.BAD_REQUEST, GradingConstants.INVALID_DISPOSITION,
                        GradingConstants.INVALID_DISPOSITION_MESSAGE);
            }
        }
        List<UUID> outstanding = candidates.stream().filter(candidate -> !candidate.submitted())
                .map(AttemptGradingCandidateView::attemptPublicId).toList();
        if (!dispositions.keySet().containsAll(outstanding) || dispositions.size() != outstanding.size()) {
            throw error(HttpStatus.CONFLICT, GradingConstants.OUTSTANDING_DISPOSITION_REQUIRED,
                    GradingConstants.OUTSTANDING_DISPOSITION_REQUIRED_MESSAGE);
        }
        return dispositions;
    }

    private GradingCohortPreviewResponse toPreview(UUID sessionPublicId, String previewVersion,
            GradingCohort cohort, List<GradingCohortMember> members, List<String> reasons) {
        return new GradingCohortPreviewResponse(sessionPublicId, previewVersion, true, cohort.getPublicId(),
                cohort.getMarkingMode().name(), (int) members.stream().filter(member -> !member.isExcluded()).count(),
                (int) members.stream().filter(com.pte.scoring.domain.GradingCohortMember::isExcluded).count(),
                members.stream().map(member -> new GradingCohortAttemptResponse(member.getAttemptPublicId(),
                        member.getStudentPublicId(), member.getAttemptStatusSnapshot(), member.isExcluded(),
                        member.getDispositionReason())).toList(), reasons);
    }

    private GradingCohortResponse response(GradingCohort cohort, GradingEvaluation evaluation) {
        return new GradingCohortResponse(cohort.getPublicId(), cohort.getSessionPublicId(), cohort.getCohortVersion(),
                cohort.getStatus().name(), cohort.getMarkingMode().name(), evaluation.requiredAttemptCount(),
                evaluation.excludedAttemptCount(), evaluation.expectedItemCount(), evaluation.satisfiedItemCount(),
                cohort.getStatus() == GradingCohortStatus.COMPLETED, evaluation.blockingReasons());
    }

    private String previewVersion(UUID sessionPublicId, List<AttemptGradingCandidateView> candidates) {
        String input = sessionPublicId + "|" + candidates.stream()
                .sorted(Comparator.comparing(AttemptGradingCandidateView::attemptPublicId))
                .map(candidate -> candidate.attemptPublicId() + "|" + candidate.studentPublicId() + "|"
                        + candidate.status() + "|" + candidate.versionToken())
                .collect(Collectors.joining(";"));
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(GradingConstants.SHA256_UNAVAILABLE, ex);
        }
    }

    private GradingCohortException error(HttpStatus status, String code, String message) {
        return new GradingCohortException(status, code, null, message);
    }
}
