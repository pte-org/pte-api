package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.attempt.dto.response.AttemptGradingCandidateView;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.identity.IdentityService;
import com.pte.media.MediaService;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.GradingAssignment;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ExaminerAnswerContentKind;
import com.pte.scoring.domain.enums.ExaminerWorkflowStatus;
import com.pte.scoring.domain.enums.ExaminerQueueStatus;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.AiScoreDetailResponse;
import com.pte.scoring.dto.response.ExaminerAnswerDetailResponse;
import com.pte.scoring.dto.response.ExaminerAnswerPayloadResponse;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerPromptOptionResponse;
import com.pte.scoring.dto.response.ExaminerPromptResponse;
import com.pte.scoring.dto.response.ExaminerQueueResponse;
import com.pte.scoring.dto.response.ExaminerReportsResponse;
import com.pte.scoring.dto.response.ExaminerResponseOptionResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.dto.response.ExaminerWorkflowView;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.dto.response.AnswerPayloadKind;
import com.pte.scoring.internal.dto.response.DecodedAnswerPayload;
import com.pte.scoring.internal.repository.ExaminerWorkspaceReadRepository;
import com.pte.scoring.domain.ScoringDomainConstants;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerDecisionResponse;
import com.pte.scoring.internal.exception.ConfirmAiMismatchException;
import com.pte.scoring.internal.exception.ExaminerCohortRequiredException;
import com.pte.scoring.internal.exception.ExaminerReceiptConflictException;
import com.pte.scoring.internal.exception.ExaminerScoreConflictException;
import com.pte.scoring.internal.exception.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.InvalidExaminerScoreException;
import com.pte.scoring.internal.exception.InvalidDecisionKindException;
import com.pte.scoring.internal.exception.InvalidExaminerQueueStatusException;
import com.pte.scoring.internal.exception.OldPayloadUnsupportedException;
import com.pte.scoring.internal.exception.StaleReviewVersionException;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.session.SessionService;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Owns Examiner queue, assignment-scoped blind detail, and immutable score submission. */
@Service
public class ExaminerScoringService {

    private static final String EXAMINER_SOURCE = "EXAMINER";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerWorkQueueRepository workQueueRepository;
    private final GradingAssignmentRepository assignmentRepository;
    private final AiEvaluationRepository scoringAnswerRepository;
    private final ExaminerEvaluationRepository examinerEvaluationRepository;
    private final SessionGradingGateway gateway;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final AnswerContentReader contentReader;
    private final IdentityService identityService;
    private final AttemptService attemptService;
    private final MediaService mediaService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final SessionService sessionService;
    private final ExaminerWorkspaceReadRepository readRepository;
    private final ExaminerWorkspaceQueryService examinerWorkspaceQueryService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final java.time.Clock clock;
    private final AiScoreDetailQueryService aiDetails;

    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            GradingAssignmentRepository assignmentRepository,
            AiEvaluationRepository scoringAnswerRepository,
            ExaminerEvaluationRepository examinerEvaluationRepository,
            SessionGradingGateway gateway,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, AnswerContentReader contentReader,
            IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            SessionService sessionService,
            ExaminerWorkspaceReadRepository readRepository,
            ExaminerWorkspaceQueryService examinerWorkspaceQueryService,
            ExaminerWorkflowQueryService workflowQueryService,
            java.time.Clock clock, AiScoreDetailQueryService aiDetails) {
        this.workQueueRepository = workQueueRepository;
        this.assignmentRepository = assignmentRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerEvaluationRepository = examinerEvaluationRepository;
        this.gateway = gateway;
        this.eligibilityQueryService = eligibilityQueryService;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.contentReader = contentReader;
        this.identityService = identityService;
        this.attemptService = attemptService;
        this.mediaService = mediaService;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.sessionService = sessionService;
        this.readRepository = readRepository;
        this.examinerWorkspaceQueryService = examinerWorkspaceQueryService;
        this.workflowQueryService = workflowQueryService;
        this.clock = clock != null ? clock : java.time.Clock.systemUTC();
        this.aiDetails = aiDetails;
    }

    @Transactional(readOnly = true)
    public ExaminerWorkspaceOverviewResponse getWorkspaceOverview(CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        ExaminerWorkspaceOverviewResponse overview;
        if (examinerWorkspaceQueryService != null) {
            overview = examinerWorkspaceQueryService.getWorkspaceOverview(examiner);
        } else if (readRepository != null) {
            overview = readRepository.getWorkspaceOverview(examiner.tenantId(), examiner.userId(),
                    clock != null ? clock.instant() : Instant.now());
        } else {
            throw new UnsupportedOperationException("Workspace overview read repository not configured");
        }
        if (overview == null) {
            return null;
        }
        Map<UUID, String> names = sessionNames(overview.priorityItems().stream()
                .map(com.pte.scoring.dto.response.ExaminerQueueItemResponse::sessionPublicId).toList(),
                examiner.tenantId());
        return new ExaminerWorkspaceOverviewResponse(overview.asOf(), overview.scope(), overview.counts(),
                overview.progress(), overview.priorityItems().stream()
                        .map(item -> item.withSessionName(names.get(item.sessionPublicId()))).toList());
    }

    /** Session display names for the given ids; absent when the session module is unavailable or has no match. */
    private Map<UUID, String> sessionNames(Collection<UUID> sessionPublicIds, UUID tenantId) {
        if (sessionService == null || sessionPublicIds.isEmpty()) {
            return Map.of();
        }
        return sessionService.findSummaries(Set.copyOf(sessionPublicIds), tenantId).stream()
                .filter(summary -> summary.name() != null)
                .collect(Collectors.toMap(SessionSummaryView::publicId, SessionSummaryView::name, (a, b) -> a));
    }

    @Transactional(readOnly = true)
    public ExaminerReportsResponse getReports(Instant fromDate, Instant toDate, String taskType,
            String status, UUID sessionPublicId, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        if (readRepository != null) {
            ExaminerReportsResponse reports = readRepository.getReports(examiner.tenantId(), examiner.userId(),
                    fromDate, toDate, taskType, status, sessionPublicId);
            if (reports == null) {
                return null;
            }
            Map<UUID, String> names = sessionNames(reports.recentFinalizedItems().stream()
                    .map(ExaminerReportsResponse.ExaminerReportRecentItem::sessionPublicId).toList(),
                    examiner.tenantId());
            return new ExaminerReportsResponse(reports.summary(), reports.taskTypeBreakdowns(),
                    reports.recentFinalizedItems().stream()
                            .map(item -> item.withSessionName(names.get(item.sessionPublicId()))).toList());
        }
        throw new UnsupportedOperationException("Workspace read repository not configured");
    }

    @Transactional(readOnly = true)
    public ExaminerQueueResponse listWork(String requestedStatus, UUID sessionPublicId, String search, String sort,
            Integer requestedPage, Integer requestedSize, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        ExaminerQueueStatus status = parseStatus(requestedStatus);
        int page = requestedPage == null || requestedPage < 0 ? 0 : requestedPage;
        int size = requestedSize == null || requestedSize <= 0 ? DEFAULT_PAGE_SIZE
                : Math.min(requestedSize, MAX_PAGE_SIZE);
        Page<com.pte.scoring.dto.response.ExaminerQueueItemResponse> result;
        if (readRepository != null) {
            result = readRepository.findQueue(
                    examiner.tenantId(), examiner.userId(), sessionPublicId, status.name(), search, sort, PageRequest.of(page, size));
        } else {
            result = workQueueRepository.findQueue(
                    examiner.tenantId(), examiner.userId(), sessionPublicId, status.name(), PageRequest.of(page, size));
        }
        Map<UUID, String> names = sessionNames(result.getContent().stream()
                .map(com.pte.scoring.dto.response.ExaminerQueueItemResponse::sessionPublicId).toList(),
                examiner.tenantId());
        List<com.pte.scoring.dto.response.ExaminerQueueItemResponse> items = result.getContent().stream()
                .map(item -> item.withSessionName(names.get(item.sessionPublicId()))).toList();
        return new ExaminerQueueResponse(items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ExaminerQueueResponse listWork(String requestedStatus, UUID sessionPublicId, Integer requestedPage,
            Integer requestedSize, CurrentUser caller) {
        return listWork(requestedStatus, sessionPublicId, null, null, requestedPage, requestedSize, caller);
    }

    @Transactional(readOnly = true)
    public ExaminerAttemptDetailResponse getAttempt(UUID sessionPublicId, UUID attemptPublicId, CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        GradingAssignment assignment = workQueueRepository
                .findOwnedAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId())
                .orElseThrow(ExaminerWorkNotFoundException::new);

        ExaminerWorkflowView workflow = workflowQueryService != null
                ? workflowQueryService.buildWorkflowView(assignment, examiner.tenantId())
                : null;

        boolean isPublished = isSessionPublished(examiner.tenantId(), sessionPublicId);
        if (isPublished) {
            if (workflow == null || workflow.status() != ExaminerWorkflowStatus.COMPLETED) {
                throw new ExaminerWorkNotFoundException();
            }
        }

        List<AiEvaluation> eligibleAnswers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, examiner.tenantId(),
                        List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();
        if (eligibleAnswers.isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }

        SessionGradingGateway.Grading cohort = gateway.grading(examiner.tenantId(), sessionPublicId).orElse(null);
        AttemptGradingCandidateView member = cohort == null ? null
                : attemptService.getGradingCandidate(attemptPublicId, examiner.tenantId()).orElse(null);
        SessionGradingGateway.Publication sessionState = gateway.publication(examiner.tenantId(), sessionPublicId)
                .orElse(null);

        boolean sessionPublishedState = sessionState != null;
        String publicationState = sessionPublishedState ? "PUBLISHED" : "UNPUBLISHED";

        boolean attemptCanFinalize = !sessionPublishedState
                && cohort != null
                && cohort.markingMode() == GradingMarkingMode.MANUAL_EXAMINER
                && (cohort.status() == GradingCohortStatus.FROZEN || cohort.status() == GradingCohortStatus.COMPLETED)
                && (member != null && !member.excluded());

        String attemptBlockingReason = sessionPublishedState ? "ALREADY_PUBLISHED"
                : cohort == null ? "WAITING_FOR_HOST_FREEZE"
                : cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER ? "COHORT_NOT_MANUAL_EXAMINER"
                : (cohort.status() != GradingCohortStatus.FROZEN && cohort.status() != GradingCohortStatus.COMPLETED) ? "WAITING_FOR_HOST_FREEZE"
                : (member == null || member.excluded()) ? "EXCLUDED_FROM_COHORT"
                : null;

        Map<UUID, ExaminerEvaluation> scoresByAnswer = examinerEvaluationRepository
                .findSubmittedByAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId).stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));
        Map<UUID, SubmittedAnswerView> contentByAnswer = contentReader.forAttempt(attemptPublicId,
                examiner.tenantId());
        List<UUID> pinnedItemPublicIds = eligibleAnswers.stream()
                .map(AiEvaluation::getItemPublicId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, AttemptExaminerPromptView> promptsByItem = attemptService
                .getExaminerPrompts(attemptPublicId, sessionPublicId, examiner.tenantId(), pinnedItemPublicIds);

        Map<UUID, DecodedAnswerPayload> decodedByAnswer = new HashMap<>();
        Set<UUID> mediaPublicIds = new LinkedHashSet<>();
        for (AiEvaluation answer : eligibleAnswers) {
            AttemptExaminerPromptView prompt = answer.getItemPublicId() == null
                    ? null : promptsByItem.get(answer.getItemPublicId());
            SubmittedAnswerView content = contentByAnswer.get(answer.getAnswerPublicId());
            if (prompt == null || content == null) {
                throw new ExaminerWorkNotFoundException();
            }
            DecodedAnswerPayload decoded = answerPayloadDecoder.decode(content);
            decodedByAnswer.put(answer.getAnswerPublicId(), decoded);
            if (decoded.kind() == AnswerPayloadKind.AUDIO && decoded.mediaPublicId() != null) {
                mediaPublicIds.add(decoded.mediaPublicId());
            }
            if (prompt.audioPromptRef() != null) {
                mediaPublicIds.add(prompt.audioPromptRef());
            }
            if (prompt.imagePromptRef() != null) {
                mediaPublicIds.add(prompt.imagePromptRef());
            }
        }
        Map<UUID, String> mediaUrls = mediaPublicIds.isEmpty() ? Map.of()
                : mediaService.presignGetAll(mediaPublicIds, ExaminerScoringConstants.MEDIA_URL_TTL_SECONDS,
                        examiner.tenantId()).entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> entry.getValue().url()));

        // Only reached after the assignment check, and keyed by the verified answers rather than a client id.
        Map<UUID, AiScoreDetailResponse> aiByAnswer = aiDetails == null ? Map.of()
                : aiDetails.findAll(examiner.tenantId(), eligibleAnswers);
        List<ExaminerAnswerDetailResponse> details = eligibleAnswers.stream()
                .map(answer -> toAnswerDetail(answer, scoresByAnswer.get(answer.getAnswerPublicId()),
                        promptsByItem.get(answer.getItemPublicId()),
                        decodedByAnswer.get(answer.getAnswerPublicId()),
                        contentByAnswer.get(answer.getAnswerPublicId()), mediaUrls, examiner,
                        assignment, cohort, member, sessionState, attemptCanFinalize, attemptBlockingReason,
                        aiByAnswer.getOrDefault(answer.getAnswerPublicId(), AiScoreDetailResponse.pending())))
                .sorted(Comparator.comparingInt(answer -> answer.prompt().orderIndex()))
                .toList();
        int submittedCount = (int) details.stream().filter(answer -> "SUBMITTED".equals(answer.status())).count();
        return new ExaminerAttemptDetailResponse(attemptPublicId, sessionPublicId, assignment.getAssignedAt(),
                assignment.getEligibleAnswerCount(), submittedCount, details,
                cohort != null ? cohort.markingMode().name() : null,
                cohort != null ? cohort.status().name() : null,
                publicationState, attemptCanFinalize, attemptBlockingReason, workflow,
                sessionNames(List.of(sessionPublicId), examiner.tenantId()).get(sessionPublicId),
                (int) workQueueRepository.countOrdinal(examiner.tenantId(), examiner.userId(), sessionPublicId,
                        assignment.getAssignedAt(), assignment.getId()));
    }

    @Transactional
    public ExaminerScoreSubmissionResponse submitScore(UUID answerPublicId, SubmitExaminerScoreRequest request,
            CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        if (request == null || request.score() == null || request.score() < 0 || request.score() > 100) {
            throw new InvalidExaminerScoreException();
        }
        AiEvaluation answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
                .orElseThrow(ExaminerWorkNotFoundException::new);
        if (!answer.getTenantId().equals(examiner.tenantId())
                || !assignmentRepository.existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
                        examiner.tenantId(), answer.getSessionPublicId(), answer.getAttemptPublicId(),
                        examiner.userId())
                || !eligibilityQueryService.isAiEligible(answer)) {
            throw new ExaminerWorkNotFoundException();
        }

        if (sessionService != null) {
            sessionService.lockForScoreReviewMutation(answer.getSessionPublicId(), examiner.tenantId());
        }

        entityManager.lock(answer, LockModeType.PESSIMISTIC_WRITE);
        entityManager.refresh(answer, LockModeType.PESSIMISTIC_WRITE);
        lockSessionPublicationIfPresent(answer.getTenantId(), answer.getSessionPublicId());

        GradingAssignment assignment = assignmentRepository
                .findByTenantIdAndSessionPublicIdAndExaminerPublicId(
                        examiner.tenantId(), answer.getSessionPublicId(), examiner.userId())
                .stream()
                .filter(a -> a.getAttemptPublicId().equals(answer.getAttemptPublicId()))
                .findFirst().orElse(null);

        if (assignment != null && workflowQueryService != null) {
            ExaminerWorkflowView workflow = workflowQueryService.buildWorkflowView(assignment, examiner.tenantId());
            if (workflow != null && "ATOMIC_DRAFT".equals(workflow.mode())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Single-item score submission is disabled for ATOMIC_DRAFT workflow. Use draft and atomic finalize.");
            }
        }

        SessionGradingGateway.Grading cohort = gateway.grading(examiner.tenantId(), answer.getSessionPublicId())
                .orElse(null);
        AttemptGradingCandidateView member = cohort == null ? null
                : attemptService.getGradingCandidate(answer.getAttemptPublicId(), examiner.tenantId()).orElse(null);
        SessionGradingGateway.Publication sessionState = gateway
                .publication(examiner.tenantId(), answer.getSessionPublicId()).orElse(null);

        if (cohort != null) {
            if (cohort.markingMode() != GradingMarkingMode.MANUAL_EXAMINER) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.INCOMPATIBLE_COHORT_MODE);
            }
            if (cohort.status() != GradingCohortStatus.FROZEN && cohort.status() != GradingCohortStatus.COMPLETED) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.COHORT_NOT_FROZEN);
            }
            if (member == null || member.excluded()) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.EXCLUDED_COHORT_MEMBER);
            }
            if (request.decisionKind() == null || request.requestPublicId() == null || request.expectedReviewVersion() == null) {
                throw new OldPayloadUnsupportedException();
            }
            if (request.decisionKind() == ExaminerDecisionKind.LEGACY_IMPORTED) {
                throw new InvalidDecisionKindException();
            }
        } else if (request.decisionKind() != null) {
            throw new ExaminerCohortRequiredException(ExaminerScoringConstants.MISSING_MANUAL_COHORT);
        }

        if (request.decisionKind() == ExaminerDecisionKind.LEGACY_IMPORTED) {
            throw new InvalidDecisionKindException();
        }

        // Check cross-answer request key reuse
        if (request.requestPublicId() != null) {
            Optional<ExaminerEvaluation> existingWithKey = examinerEvaluationRepository
                    .findSubmittedByRequest(
                            examiner.tenantId(), examiner.userId(), request.requestPublicId());
            if (existingWithKey.isPresent() && !existingWithKey.get().getAnswerPublicId().equals(answerPublicId)) {
                throw new ExaminerReceiptConflictException();
            }
        }

        // Idempotent retry lookup before current AI validation or freshness check
        ExaminerEvaluation row = examinerEvaluationRepository
                .findByAnswerPublicIdAndTenantId(answerPublicId, examiner.tenantId()).orElse(null);
        ExaminerEvaluation existing = row != null && row.isSubmitted() ? row : null;
        SubmittedAnswerView content = contentReader.one(answerPublicId, examiner.tenantId()).orElse(null);
        if (existing != null) {
            String retryFingerprint = null;
            if (request.decisionKind() != null) {
                retryFingerprint = examinerReviewVersionService.computeRequestFingerprint(
                        answerPublicId, request.score(), request.decisionKind(),
                        request.expectedReviewVersion(), request.reviewedAiVersion());
            }
            try {
                existing.requireIdenticalRetry(examiner.userId(), request.score(),
                        request.requestPublicId(), retryFingerprint);
            } catch (IllegalStateException ex) {
                if (ScoringDomainConstants.EXAMINER_RECEIPT_CONFLICT.equals(ex.getMessage())) {
                    throw new ExaminerReceiptConflictException();
                }
                throw new ExaminerScoreConflictException();
            }
            String currentReviewVersion = examinerReviewVersionService.computeReviewVersion(
                    answer, content, assignment, cohort, member, existing, sessionState);
            return toSubmission(existing, currentReviewVersion);
        }

        // New decision: validate AI reference and review version freshness
        Integer reviewedAiScore = null;
        String reviewedAiVersion = request.reviewedAiVersion();
        if (request.decisionKind() == ExaminerDecisionKind.CONFIRM_AI) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() != ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                    || !Objects.equals(aiRef.version(), request.reviewedAiVersion())
                    || !Objects.equals(aiRef.score(), request.score())) {
                throw new ConfirmAiMismatchException();
            }
            reviewedAiScore = aiRef.score();
            reviewedAiVersion = aiRef.version();
        } else if (request.decisionKind() == ExaminerDecisionKind.MANUAL) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() == ExaminerAiAvailability.AVAILABLE) {
                if (request.reviewedAiVersion() != null && !Objects.equals(aiRef.version(), request.reviewedAiVersion())) {
                    throw new StaleReviewVersionException();
                }
                reviewedAiScore = aiRef.score();
                reviewedAiVersion = aiRef.version();
            } else {
                reviewedAiScore = null;
                reviewedAiVersion = null;
            }
        }

        if (request.decisionKind() != null) {
            String currentReviewVersion = examinerReviewVersionService.computeReviewVersion(
                    answer, content, assignment, cohort, member, null, sessionState);
            if (!Objects.equals(currentReviewVersion, request.expectedReviewVersion())) {
                throw new StaleReviewVersionException();
            }
        }

        String fingerprint = null;
        if (request.decisionKind() != null) {
            fingerprint = examinerReviewVersionService.computeRequestFingerprint(
                    answerPublicId, request.score(), request.decisionKind(),
                    request.expectedReviewVersion(), request.reviewedAiVersion());
        }

        Instant submittedAt = Instant.now();
        ExaminerEvaluation toSave;
        if (row != null) {
            row.submit(request.score(), submittedAt, request.decisionKind(), reviewedAiScore, reviewedAiVersion,
                    request.expectedReviewVersion(), request.requestPublicId(), fingerprint);
            toSave = row;
        } else {
            toSave = ExaminerEvaluation.submittedDirectly(answer.getTenantId(), answer.getSessionPublicId(),
                    answer.getAttemptPublicId(), answer.getAnswerPublicId(), examiner.userId(), request.score(),
                    submittedAt, request.decisionKind(), reviewedAiScore, reviewedAiVersion,
                    request.expectedReviewVersion(), request.requestPublicId(), fingerprint);
        }

        ExaminerEvaluation saved;
        try {
            saved = examinerEvaluationRepository.saveAndFlush(toSave);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ExaminerReceiptConflictException();
        }

        if (request.decisionKind() != null) {
            attemptService.selectScoreSource(List.of(answer.getAnswerPublicId()), answer.getTenantId(),
                    EXAMINER_SOURCE, examiner.userId(), saved.getSubmittedAt(),
                    Map.of(answer.getAnswerPublicId(), saved.getPublicId()));
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.pte.scoring.ScoringProgressChangedEvent(
                    answer.getTenantId(), answer.getSessionPublicId()));
        }
        String updatedReviewVersion = examinerReviewVersionService.computeReviewVersion(
                answer, content, assignment, cohort, member, saved, sessionState);
        return toSubmission(saved, updatedReviewVersion);
    }

    private ExaminerAnswerDetailResponse toAnswerDetail(AiEvaluation answer, ExaminerEvaluation savedScore,
            AttemptExaminerPromptView prompt, DecodedAnswerPayload decoded, SubmittedAnswerView content,
            Map<UUID, String> mediaUrls,
            CurrentUser examiner, GradingAssignment assignment, SessionGradingGateway.Grading cohort,
            AttemptGradingCandidateView member, SessionGradingGateway.Publication sessionState,
            boolean attemptCanFinalize, String attemptBlockingReason, AiScoreDetailResponse ai) {
        if (savedScore != null && !savedScore.getExaminerPublicId().equals(examiner.userId())) {
            throw new ExaminerWorkNotFoundException();
        }
        if (prompt == null || decoded == null) {
            throw new ExaminerWorkNotFoundException();
        }
        String answerMediaUrl = decoded.kind() == AnswerPayloadKind.AUDIO
                ? mediaUrl(mediaUrls, decoded.mediaPublicId()) : null;
        ExaminerPromptResponse safePrompt = new ExaminerPromptResponse(prompt.orderIndex(), prompt.section(),
                prompt.taskType(), prompt.title(), prompt.promptText(),
                mediaUrl(mediaUrls, prompt.audioPromptRef()), mediaUrl(mediaUrls, prompt.imagePromptRef()),
                prompt.minWordCount(), prompt.maxWordCount(),
                prompt.options().stream().map(option -> new ExaminerPromptOptionResponse(
                        option.orderIndex(), option.text(), option.blankIndex())).toList());
        ExaminerAnswerPayloadResponse safeResponse = new ExaminerAnswerPayloadResponse(
                ExaminerAnswerContentKind.valueOf(decoded.kind().name()), decoded.text(), answerMediaUrl,
                decoded.options() == null ? List.of() : decoded.options().stream()
                        .map(option -> new ExaminerResponseOptionResponse(
                                option.orderIndex(), option.text(), option.selectedByStudent()))
                        .toList(),
                decoded.gapValues(), decoded.wordIndices());

        ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
        ExaminerDecisionResponse decision = savedScore != null
                ? new ExaminerDecisionResponse(savedScore.getStatus().name(), savedScore.getScore(),
                        savedScore.getDecisionKind(), savedScore.getSubmittedAt(),
                        savedScore.getReviewedAiScore(), savedScore.getReviewedAiVersion(),
                        savedScore.getRequestPublicId())
                : null;
        String reviewVersion = examinerReviewVersionService.computeReviewVersion(
                answer, content, assignment, cohort, member, savedScore, sessionState);
        boolean answerCanFinalize = attemptCanFinalize && savedScore == null;
        String answerBlockingReason = savedScore != null ? "ALREADY_FINALIZED" : attemptBlockingReason;

        return new ExaminerAnswerDetailResponse(answer.getAnswerPublicId(), answer.getTaskType(), safePrompt,
                safeResponse, savedScore == null ? "PENDING" : ExaminerAnswerScoreStatus.SUBMITTED.name(),
                savedScore == null ? null : savedScore.getScore(),
                savedScore == null ? null : savedScore.getSubmittedAt(),
                aiRef, decision, reviewVersion, answerCanFinalize, answerBlockingReason, ai);
    }

    private String mediaUrl(Map<UUID, String> mediaUrls, UUID mediaPublicId) {
        return mediaPublicId == null ? null : mediaUrls.get(mediaPublicId);
    }

    /** The session row is already locked by the caller, so publication can only be read, not raced. */
    private void lockSessionPublicationIfPresent(UUID tenantId, UUID sessionPublicId) {
        if (gateway.isPublished(tenantId, sessionPublicId)) {
            throw new ExaminerScoreConflictException();
        }
    }

    private boolean isSessionPublished(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }

    private ExaminerScoreSubmissionResponse toSubmission(ExaminerEvaluation score, String reviewVersion) {
        ExaminerDecisionResponse decision = new ExaminerDecisionResponse(
                score.getStatus().name(), score.getScore(), score.getDecisionKind(),
                score.getSubmittedAt(), score.getReviewedAiScore(), score.getReviewedAiVersion(),
                score.getRequestPublicId());
        return new ExaminerScoreSubmissionResponse(score.getAnswerPublicId(), score.getScore(),
                score.getStatus().name(), score.getSubmittedAt(), decision, "EXAMINER", reviewVersion);
    }

    private ExaminerQueueStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            return ExaminerQueueStatus.ALL;
        }
        try {
            return ExaminerQueueStatus.valueOf(requestedStatus.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidExaminerQueueStatusException();
        }
    }

    private CurrentUser requireActiveExaminer(CurrentUser caller) {
        if (caller == null || caller.userId() == null || caller.tenantId() == null
                || !caller.hasRole("EXAMINER")) {
            throw new AccessDeniedException(ExaminerScoringConstants.AUTHENTICATED_TENANT_EXAMINER_REQUIRED);
        }
        if (identityService.findActiveExaminers(caller.tenantId(), List.of(caller.userId())).isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }
        return caller;
    }
}
