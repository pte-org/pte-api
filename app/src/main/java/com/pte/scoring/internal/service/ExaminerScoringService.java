package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.AttemptExaminerPromptView;
import com.pte.identity.IdentityService;
import com.pte.media.MediaService;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ExaminerAttemptAssignment;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ExaminerAnswerContentKind;
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
import com.pte.scoring.domain.GradingCohort;
import com.pte.scoring.domain.GradingCohortMember;
import com.pte.scoring.domain.ScoringDomainConstants;
import com.pte.scoring.domain.enums.ExaminerAiAvailability;
import com.pte.scoring.domain.enums.ExaminerDecisionKind;
import com.pte.scoring.domain.enums.GradingCohortStatus;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.dto.response.ExaminerAiReferenceResponse;
import com.pte.scoring.dto.response.ExaminerDecisionResponse;
import com.pte.scoring.domain.enums.ScoreSource;
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
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ExaminerAttemptAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.GradingCohortMemberRepository;
import com.pte.scoring.internal.repository.GradingCohortRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.scoring.domain.enums.ExaminerAnswerScoreStatus;
import com.pte.session.SessionService;
import com.pte.session.dto.response.SessionSummaryView;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
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

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerWorkQueueRepository workQueueRepository;
    private final ExaminerAttemptAssignmentRepository assignmentRepository;
    private final ScoringAnswerRepository scoringAnswerRepository;
    private final ExaminerAnswerScoreRepository examinerAnswerScoreRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ScoringEligibilityQueryService eligibilityQueryService;
    private final AnswerPayloadDecoder answerPayloadDecoder;
    private final IdentityService identityService;
    private final AttemptService attemptService;
    private final MediaService mediaService;
    private final EntityManager entityManager;
    private final ApplicationEventPublisher eventPublisher;
    private final ExaminerReviewVersionService examinerReviewVersionService;
    private final GradingCohortRepository gradingCohortRepository;
    private final GradingCohortMemberRepository gradingCohortMemberRepository;
    private final SessionService sessionService;
    private final ExaminerWorkspaceReadRepository readRepository;
    private final ExaminerWorkspaceQueryService examinerWorkspaceQueryService;
    private final ExaminerWorkflowQueryService workflowQueryService;
    private final java.time.Clock clock;
    private final AiScoreDetailQueryService aiDetails;

    @Autowired
    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            GradingCohortRepository gradingCohortRepository,
            GradingCohortMemberRepository gradingCohortMemberRepository,
            SessionService sessionService,
            ExaminerWorkspaceReadRepository readRepository,
            ExaminerWorkspaceQueryService examinerWorkspaceQueryService,
            ExaminerWorkflowQueryService workflowQueryService,
            java.time.Clock clock, AiScoreDetailQueryService aiDetails) {
        this.workQueueRepository = workQueueRepository;
        this.assignmentRepository = assignmentRepository;
        this.scoringAnswerRepository = scoringAnswerRepository;
        this.examinerAnswerScoreRepository = examinerAnswerScoreRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.eligibilityQueryService = eligibilityQueryService;
        this.answerPayloadDecoder = answerPayloadDecoder;
        this.identityService = identityService;
        this.attemptService = attemptService;
        this.mediaService = mediaService;
        this.entityManager = entityManager;
        this.eventPublisher = eventPublisher;
        this.examinerReviewVersionService = examinerReviewVersionService;
        this.gradingCohortRepository = gradingCohortRepository;
        this.gradingCohortMemberRepository = gradingCohortMemberRepository;
        this.sessionService = sessionService;
        this.readRepository = readRepository;
        this.examinerWorkspaceQueryService = examinerWorkspaceQueryService;
        this.workflowQueryService = workflowQueryService;
        this.clock = clock != null ? clock : java.time.Clock.systemUTC();
        this.aiDetails = aiDetails;
    }

    /** Compatibility constructor for callers that do not need the AI score breakdown. */
    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            GradingCohortRepository gradingCohortRepository,
            GradingCohortMemberRepository gradingCohortMemberRepository,
            SessionService sessionService,
            ExaminerWorkspaceReadRepository readRepository,
            ExaminerWorkspaceQueryService examinerWorkspaceQueryService,
            ExaminerWorkflowQueryService workflowQueryService,
            java.time.Clock clock) {
        this(workQueueRepository, assignmentRepository, scoringAnswerRepository, examinerAnswerScoreRepository,
                sessionStateRepository, eligibilityQueryService, answerPayloadDecoder, identityService,
                attemptService, mediaService, entityManager, eventPublisher, examinerReviewVersionService,
                gradingCohortRepository, gradingCohortMemberRepository, sessionService, readRepository,
                examinerWorkspaceQueryService, workflowQueryService, clock, null);
    }

    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            GradingCohortRepository gradingCohortRepository,
            GradingCohortMemberRepository gradingCohortMemberRepository,
            SessionService sessionService,
            ExaminerWorkspaceReadRepository readRepository,
            ExaminerWorkspaceQueryService examinerWorkspaceQueryService,
            java.time.Clock clock) {
        this(workQueueRepository, assignmentRepository, scoringAnswerRepository, examinerAnswerScoreRepository,
                sessionStateRepository, eligibilityQueryService, answerPayloadDecoder, identityService,
                attemptService, mediaService, entityManager, eventPublisher, examinerReviewVersionService,
                gradingCohortRepository, gradingCohortMemberRepository, sessionService, readRepository,
                examinerWorkspaceQueryService, null, clock);
    }

    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            GradingCohortRepository gradingCohortRepository,
            GradingCohortMemberRepository gradingCohortMemberRepository,
            SessionService sessionService) {
        this(workQueueRepository, assignmentRepository, scoringAnswerRepository, examinerAnswerScoreRepository,
                sessionStateRepository, eligibilityQueryService, answerPayloadDecoder, identityService,
                attemptService, mediaService, entityManager, eventPublisher, examinerReviewVersionService,
                gradingCohortRepository, gradingCohortMemberRepository, sessionService, null, null, null);
    }

    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager,
            ApplicationEventPublisher eventPublisher,
            ExaminerReviewVersionService examinerReviewVersionService,
            GradingCohortRepository gradingCohortRepository,
            GradingCohortMemberRepository gradingCohortMemberRepository) {
        this(workQueueRepository, assignmentRepository, scoringAnswerRepository, examinerAnswerScoreRepository,
                sessionStateRepository, eligibilityQueryService, answerPayloadDecoder, identityService,
                attemptService, mediaService, entityManager, eventPublisher, examinerReviewVersionService,
                gradingCohortRepository, gradingCohortMemberRepository, null);
    }

    /** Compatibility constructor for focused Examiner workflow tests. */
    public ExaminerScoringService(ExaminerWorkQueueRepository workQueueRepository,
            ExaminerAttemptAssignmentRepository assignmentRepository,
            ScoringAnswerRepository scoringAnswerRepository,
            ExaminerAnswerScoreRepository examinerAnswerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoringEligibilityQueryService eligibilityQueryService,
            AnswerPayloadDecoder answerPayloadDecoder, IdentityService identityService,
            AttemptService attemptService, MediaService mediaService, EntityManager entityManager) {
        this(workQueueRepository, assignmentRepository, scoringAnswerRepository, examinerAnswerScoreRepository,
                sessionStateRepository, eligibilityQueryService, answerPayloadDecoder, identityService,
                attemptService, mediaService, entityManager, null,
                new ExaminerReviewVersionService(), null, null, null);
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
        ExaminerAttemptAssignment assignment = workQueueRepository
                .findOwnedAttempt(examiner.tenantId(), sessionPublicId, attemptPublicId, examiner.userId())
                .orElseThrow(ExaminerWorkNotFoundException::new);

        ExaminerWorkflowView workflow = workflowQueryService != null
                ? workflowQueryService.buildWorkflowView(assignment, examiner.tenantId())
                : null;

        boolean isPublished = isSessionPublished(examiner.tenantId(), sessionPublicId);
        if (isPublished) {
            if (workflow == null || workflow.status() != com.pte.scoring.domain.enums.ExaminerWorkflowStatus.COMPLETED) {
                throw new ExaminerWorkNotFoundException();
            }
        }

        List<ScoringAnswer> eligibleAnswers = scoringAnswerRepository
                .findBySessionPublicIdAndTenantIdAndAttemptPublicIdIn(sessionPublicId, examiner.tenantId(),
                        List.of(attemptPublicId))
                .stream()
                .filter(eligibilityQueryService::isAiEligible)
                .toList();
        if (eligibleAnswers.isEmpty()) {
            throw new ExaminerWorkNotFoundException();
        }

        GradingCohort cohort = gradingCohortRepository != null
                ? gradingCohortRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(
                        examiner.tenantId(), sessionPublicId).orElse(null)
                : null;
        GradingCohortMember member = (cohort != null && gradingCohortMemberRepository != null)
                ? gradingCohortMemberRepository.findByCohortPublicIdAndAttemptPublicIdAndDeletedFalse(
                        cohort.getPublicId(), attemptPublicId).orElse(null)
                : null;
        ScoringSessionState sessionState = sessionStateRepository
                .findByTenantIdAndSessionPublicId(examiner.tenantId(), sessionPublicId).orElse(null);

        boolean sessionPublishedState = sessionState != null && sessionState.getPublicationPublicId() != null;
        String publicationState = sessionPublishedState ? "PUBLISHED" : "UNPUBLISHED";

        boolean attemptCanFinalize = !sessionPublishedState
                && cohort != null
                && cohort.getMarkingMode() == GradingMarkingMode.MANUAL_EXAMINER
                && (cohort.getStatus() == GradingCohortStatus.FROZEN || cohort.getStatus() == GradingCohortStatus.COMPLETED)
                && (member != null && !member.isExcluded());

        String attemptBlockingReason = sessionPublishedState ? "ALREADY_PUBLISHED"
                : cohort == null ? "WAITING_FOR_HOST_FREEZE"
                : cohort.getMarkingMode() != GradingMarkingMode.MANUAL_EXAMINER ? "COHORT_NOT_MANUAL_EXAMINER"
                : (cohort.getStatus() != GradingCohortStatus.FROZEN && cohort.getStatus() != GradingCohortStatus.COMPLETED) ? "WAITING_FOR_HOST_FREEZE"
                : (member == null || member.isExcluded()) ? "EXCLUDED_FROM_COHORT"
                : null;

        Map<UUID, ExaminerAnswerScore> scoresByAnswer = examinerAnswerScoreRepository
                .findByTenantIdAndSessionPublicIdAndAttemptPublicId(
                        examiner.tenantId(), sessionPublicId, attemptPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        List<UUID> pinnedItemPublicIds = eligibleAnswers.stream()
                .map(ScoringAnswer::getPinnedItemPublicId).filter(Objects::nonNull).distinct().toList();
        Map<UUID, AttemptExaminerPromptView> promptsByItem = attemptService
                .getExaminerPrompts(attemptPublicId, sessionPublicId, examiner.tenantId(), pinnedItemPublicIds);

        Map<UUID, DecodedAnswerPayload> decodedByAnswer = new HashMap<>();
        Set<UUID> mediaPublicIds = new LinkedHashSet<>();
        for (ScoringAnswer answer : eligibleAnswers) {
            AttemptExaminerPromptView prompt = answer.getPinnedItemPublicId() == null
                    ? null : promptsByItem.get(answer.getPinnedItemPublicId());
            if (prompt == null) {
                throw new ExaminerWorkNotFoundException();
            }
            DecodedAnswerPayload decoded = answerPayloadDecoder.decode(answer);
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
                        promptsByItem.get(answer.getPinnedItemPublicId()),
                        decodedByAnswer.get(answer.getAnswerPublicId()), mediaUrls, examiner,
                        assignment, cohort, member, sessionState, attemptCanFinalize, attemptBlockingReason,
                        aiByAnswer.getOrDefault(answer.getAnswerPublicId(), AiScoreDetailResponse.pending())))
                .sorted(Comparator.comparingInt(answer -> answer.prompt().orderIndex()))
                .toList();
        int submittedCount = (int) details.stream().filter(answer -> "SUBMITTED".equals(answer.status())).count();
        return new ExaminerAttemptDetailResponse(attemptPublicId, sessionPublicId, assignment.getAssignedAt(),
                assignment.getEligibleAnswerCount(), submittedCount, details,
                cohort != null ? cohort.getMarkingMode().name() : null,
                cohort != null ? cohort.getStatus().name() : null,
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
        ScoringAnswer answer = scoringAnswerRepository.findByAnswerPublicId(answerPublicId)
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

        ExaminerAttemptAssignment assignment = assignmentRepository
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

        GradingCohort cohort = gradingCohortRepository != null
                ? gradingCohortRepository.findByTenantIdAndSessionPublicIdAndDeletedFalse(
                        examiner.tenantId(), answer.getSessionPublicId()).orElse(null)
                : null;
        GradingCohortMember member = (cohort != null && gradingCohortMemberRepository != null)
                ? gradingCohortMemberRepository.findByCohortPublicIdAndAttemptPublicIdAndDeletedFalse(
                        cohort.getPublicId(), answer.getAttemptPublicId()).orElse(null)
                : null;
        ScoringSessionState sessionState = sessionStateRepository
                .findByTenantIdAndSessionPublicId(examiner.tenantId(), answer.getSessionPublicId()).orElse(null);

        if (cohort != null) {
            if (cohort.getMarkingMode() != GradingMarkingMode.MANUAL_EXAMINER) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.INCOMPATIBLE_COHORT_MODE);
            }
            if (cohort.getStatus() != GradingCohortStatus.FROZEN && cohort.getStatus() != GradingCohortStatus.COMPLETED) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.COHORT_NOT_FROZEN);
            }
            if (member == null || member.isExcluded()) {
                throw new ExaminerCohortRequiredException(ExaminerScoringConstants.EXCLUDED_COHORT_MEMBER);
            }
            if (request.decisionKind() == null || request.requestPublicId() == null || request.expectedReviewVersion() == null) {
                throw new OldPayloadUnsupportedException();
            }
            if (request.decisionKind() == com.pte.scoring.domain.enums.ExaminerDecisionKind.LEGACY_IMPORTED) {
                throw new InvalidDecisionKindException();
            }
        } else if (request.decisionKind() != null && gradingCohortRepository != null) {
            throw new ExaminerCohortRequiredException(ExaminerScoringConstants.MISSING_MANUAL_COHORT);
        }

        if (request.decisionKind() == com.pte.scoring.domain.enums.ExaminerDecisionKind.LEGACY_IMPORTED) {
            throw new InvalidDecisionKindException();
        }

        // Check cross-answer request key reuse
        if (request.requestPublicId() != null) {
            Optional<ExaminerAnswerScore> existingWithKey = examinerAnswerScoreRepository
                    .findByTenantIdAndExaminerPublicIdAndRequestPublicId(
                            examiner.tenantId(), examiner.userId(), request.requestPublicId());
            if (existingWithKey.isPresent() && !existingWithKey.get().getAnswerPublicId().equals(answerPublicId)) {
                throw new ExaminerReceiptConflictException();
            }
        }

        // Idempotent retry lookup before current AI validation or freshness check
        ExaminerAnswerScore existing = examinerAnswerScoreRepository
                .findByAnswerPublicIdAndTenantId(answerPublicId, examiner.tenantId()).orElse(null);
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
                    answer, assignment, cohort, member, existing, sessionState);
            return toSubmission(existing, currentReviewVersion);
        }

        // New decision: validate AI reference and review version freshness
        Integer reviewedAiScore = null;
        String reviewedAiVersion = request.reviewedAiVersion();
        if (request.decisionKind() == com.pte.scoring.domain.enums.ExaminerDecisionKind.CONFIRM_AI) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() != com.pte.scoring.domain.enums.ExaminerAiAvailability.AVAILABLE || aiRef.score() == null
                    || !Objects.equals(aiRef.version(), request.reviewedAiVersion())
                    || !Objects.equals(aiRef.score(), request.score())) {
                throw new ConfirmAiMismatchException();
            }
            reviewedAiScore = aiRef.score();
            reviewedAiVersion = aiRef.version();
        } else if (request.decisionKind() == com.pte.scoring.domain.enums.ExaminerDecisionKind.MANUAL) {
            ExaminerAiReferenceResponse aiRef = examinerReviewVersionService.buildAiReference(answer);
            if (aiRef.availability() == com.pte.scoring.domain.enums.ExaminerAiAvailability.AVAILABLE) {
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
                    answer, assignment, cohort, member, null, sessionState);
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

        ExaminerAnswerScore toSave;
        if (request.decisionKind() != null) {
            toSave = new ExaminerAnswerScore(
                    answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getSessionPublicId(),
                    answer.getTenantId(), examiner.userId(), request.score(), Instant.now(),
                    request.decisionKind(), reviewedAiScore, reviewedAiVersion,
                    request.expectedReviewVersion(), request.requestPublicId(), fingerprint);
        } else {
            toSave = new ExaminerAnswerScore(
                    answer.getAnswerPublicId(), answer.getAttemptPublicId(), answer.getSessionPublicId(),
                    answer.getTenantId(), examiner.userId(), request.score(), Instant.now());
        }

        ExaminerAnswerScore saved;
        try {
            saved = examinerAnswerScoreRepository.saveAndFlush(toSave);
        } catch (org.springframework.dao.DataIntegrityViolationException ex) {
            throw new ExaminerReceiptConflictException();
        }
        if (saved.getId() == null) {
            saved.setId(1L);
        }
        if (saved.getPublicId() == null) {
            saved.setPublicId(UUID.randomUUID());
        }

        if (request.decisionKind() != null) {
            answer.selectScoreSource(ScoreSource.EXAMINER, examiner.userId(), saved.getSubmittedAt(), saved);
            scoringAnswerRepository.save(answer);
            scoringAnswerRepository.flush();
        }

        if (eventPublisher != null) {
            eventPublisher.publishEvent(new com.pte.scoring.ScoringProgressChangedEvent(
                    answer.getTenantId(), answer.getSessionPublicId()));
        }
        String updatedReviewVersion = examinerReviewVersionService.computeReviewVersion(
                answer, assignment, cohort, member, saved, sessionState);
        return toSubmission(saved, updatedReviewVersion);
    }

    private ExaminerAnswerDetailResponse toAnswerDetail(ScoringAnswer answer, ExaminerAnswerScore savedScore,
            AttemptExaminerPromptView prompt, DecodedAnswerPayload decoded, Map<UUID, String> mediaUrls,
            CurrentUser examiner, ExaminerAttemptAssignment assignment, GradingCohort cohort,
            GradingCohortMember member, ScoringSessionState sessionState,
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
                answer, assignment, cohort, member, savedScore, sessionState);
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

    private void lockSessionPublicationIfPresent(UUID tenantId, UUID sessionPublicId) {
        sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).ifPresent(state -> {
            entityManager.lock(state, LockModeType.PESSIMISTIC_WRITE);
            entityManager.refresh(state, LockModeType.PESSIMISTIC_WRITE);
            if (state.getPublicationPublicId() != null) {
                throw new ExaminerScoreConflictException();
            }
        });
    }

    private boolean isSessionPublished(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(state -> state.getPublicationPublicId() != null)
                .orElse(false);
    }

    private ExaminerScoreSubmissionResponse toSubmission(ExaminerAnswerScore score, String reviewVersion) {
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
