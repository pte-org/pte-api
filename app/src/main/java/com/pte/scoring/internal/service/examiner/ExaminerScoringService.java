package com.pte.scoring.internal.service.examiner;

import com.pte.scoring.internal.service.ai.AiScoreDetailQueryService;
import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.AnswerPayloadDecoder;
import com.pte.scoring.internal.service.staff.ScoringEligibilityQueryService;
import com.pte.attempt.AttemptService;
import com.pte.identity.IdentityService;
import com.pte.media.MediaService;
import com.pte.scoring.domain.enums.ExaminerQueueStatus;
import com.pte.scoring.dto.request.SubmitExaminerScoreRequest;
import com.pte.scoring.dto.response.ExaminerAttemptDetailResponse;
import com.pte.scoring.dto.response.ExaminerQueueResponse;
import com.pte.scoring.dto.response.ExaminerReportsResponse;
import com.pte.scoring.dto.response.ExaminerScoreSubmissionResponse;
import com.pte.scoring.dto.response.ExaminerWorkspaceOverviewResponse;
import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.scoring.internal.repository.ExaminerWorkspaceReadRepository;
import com.pte.scoring.internal.exception.examiner.ExaminerWorkNotFoundException;
import com.pte.scoring.internal.exception.examiner.InvalidExaminerQueueStatusException;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.internal.repository.GradingAssignmentRepository;
import com.pte.scoring.internal.repository.ExaminerWorkQueueRepository;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import jakarta.persistence.EntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Owns Examiner queue, assignment-scoped blind detail, and immutable score submission. Caller verification
 * stays here; the attempt detail and the single-answer submission run in {@link ExaminerAttemptDetailReader}
 * and {@link ExaminerDirectScoreSubmitter} inside this bean's transactions.
 */
@Service
public class ExaminerScoringService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ExaminerWorkQueueRepository workQueueRepository;
    private final IdentityService identityService;
    private final ExaminerWorkspaceReadRepository readRepository;
    private final ExaminerWorkspaceQueryService examinerWorkspaceQueryService;
    private final java.time.Clock clock;
    private final ExaminerSessionNames sessionNames;
    private final ExaminerAttemptDetailReader attemptDetailReader;
    private final ExaminerDirectScoreSubmitter scoreSubmitter;

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
        this.identityService = identityService;
        this.readRepository = readRepository;
        this.examinerWorkspaceQueryService = examinerWorkspaceQueryService;
        this.clock = clock;
        this.sessionNames = new ExaminerSessionNames(sessionService);
        this.attemptDetailReader = new ExaminerAttemptDetailReader(workQueueRepository, scoringAnswerRepository,
                examinerEvaluationRepository, gateway, eligibilityQueryService, answerPayloadDecoder,
                contentReader, attemptService, mediaService, examinerReviewVersionService, workflowQueryService,
                aiDetails, sessionNames);
        this.scoreSubmitter = new ExaminerDirectScoreSubmitter(assignmentRepository, scoringAnswerRepository,
                examinerEvaluationRepository, gateway, eligibilityQueryService, contentReader, attemptService,
                entityManager, eventPublisher, examinerReviewVersionService, sessionService,
                workflowQueryService, this.clock);
    }

    @Transactional(readOnly = true)
    public ExaminerWorkspaceOverviewResponse getWorkspaceOverview(CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        ExaminerWorkspaceOverviewResponse overview;
        if (examinerWorkspaceQueryService != null) {
            overview = examinerWorkspaceQueryService.getWorkspaceOverview(examiner);
        } else if (readRepository != null) {
            overview = readRepository.getWorkspaceOverview(examiner.tenantId(), examiner.userId(),
                    clock.instant());
        } else {
            throw new UnsupportedOperationException("Workspace overview read repository not configured");
        }
        if (overview == null) {
            return null;
        }
        Map<UUID, String> names = sessionNames.namesOf(overview.priorityItems().stream()
                .map(com.pte.scoring.dto.response.ExaminerQueueItemResponse::sessionPublicId).toList(),
                examiner.tenantId());
        return new ExaminerWorkspaceOverviewResponse(overview.asOf(), overview.scope(), overview.counts(),
                overview.progress(), overview.priorityItems().stream()
                        .map(item -> item.withSessionName(names.get(item.sessionPublicId()))).toList());
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
            Map<UUID, String> names = sessionNames.namesOf(reports.recentFinalizedItems().stream()
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
        Map<UUID, String> names = sessionNames.namesOf(result.getContent().stream()
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
        return attemptDetailReader.read(sessionPublicId, attemptPublicId, examiner);
    }

    @Transactional
    public ExaminerScoreSubmissionResponse submitScore(UUID answerPublicId, SubmitExaminerScoreRequest request,
            CurrentUser caller) {
        CurrentUser examiner = requireActiveExaminer(caller);
        return scoreSubmitter.submit(answerPublicId, request, examiner);
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
