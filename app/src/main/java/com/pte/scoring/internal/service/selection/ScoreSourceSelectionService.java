package com.pte.scoring.internal.service.selection;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.review.ScoringReviewReadQueryService;
import com.pte.attempt.AttemptService;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.dto.response.ScoreSourceAuditResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionPreviewResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionResultResponse;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The host choosing, in bulk, which independently persisted score counts for each AI-scorable answer. The choice
 * is written on the answer ({@code attempt}); this service decides whether it may be made. A choice is replayable
 * by its request id (an idempotency receipt) and every applied choice is a row of the audit trail, so the same
 * history endpoint keeps working without a table of its own.
 */
@Service
public class ScoreSourceSelectionService {

    private static final int AUDIT_HISTORY_LIMIT = 50;

    private final SessionService sessionService;
    private final AiEvaluationRepository evaluationRepository;
    private final ExaminerEvaluationRepository examinerRepository;
    private final SessionGradingGateway gateway;
    private final ScoringReviewReadQueryService readQueryService;
    private final ScoreSelectionReader selectionReader;
    private final AttemptService attemptService;
    private final IdempotencyKeyService idempotencyKeyService;
    private final AuditLogChangeWriter auditWriter;
    private final AuditHistoryReader auditReader;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public ScoreSourceSelectionService(SessionService sessionService, AiEvaluationRepository evaluationRepository,
            ExaminerEvaluationRepository examinerRepository, SessionGradingGateway gateway,
            ScoringReviewReadQueryService readQueryService, ScoreSelectionReader selectionReader,
            AttemptService attemptService,
            IdempotencyKeyService idempotencyKeyService, AuditLogChangeWriter auditWriter,
            AuditHistoryReader auditReader, JsonMapper jsonMapper,
            Clock clock) {
        this.sessionService = sessionService;
        this.evaluationRepository = evaluationRepository;
        this.examinerRepository = examinerRepository;
        this.gateway = gateway;
        this.readQueryService = readQueryService;
        this.selectionReader = selectionReader;
        this.attemptService = attemptService;
        this.idempotencyKeyService = idempotencyKeyService;
        this.auditWriter = auditWriter;
        this.auditReader = auditReader;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ScoreSourceSelectionPreviewResponse preview(UUID sessionPublicId, SelectScoreSourceRequest request,
            CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        validateRequest(request, false);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        assertNotManualExaminer(tenantId, sessionPublicId);
        List<AiEvaluation> answers = evaluationRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId);
        return previewFor(tenantId, sessionPublicId, request, answers, submittedByAnswer(tenantId, sessionPublicId),
                selectionReader.forSession(sessionPublicId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<ScoreSourceAuditResponse> auditHistory(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        return auditReader.history(tenantId, ScoreReviewConstants.AUDIT_AGGREGATE_TYPE, sessionPublicId.toString(),
                        ScoreReviewConstants.AUDIT_ACTION, 0, AUDIT_HISTORY_LIMIT).data().stream()
                .map(entry -> jsonMapper.readValue(entry.afterStateJson(), ScoreSourceAuditResponse.class))
                .toList();
    }

    @Transactional
    public ScoreSourceSelectionResultResponse apply(UUID sessionPublicId, SelectScoreSourceRequest request,
            CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        validateRequest(request, true);
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);
        assertNotManualExaminer(tenantId, sessionPublicId);
        String key = ScoreReviewConstants.IDEMPOTENCY_KEY_PREFIX + sessionPublicId + ":" + request.requestPublicId();
        IdempotencyDecision decision = idempotencyKeyService.begin(IdempotencyScope.SCORE_SELECTION, key, tenantId,
                fingerprint(request, caller.userId()), clock.instant());
        if (decision.outcome() == IdempotencyOutcome.CONFLICT) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.REQUEST_KEY_REUSED,
                    ScoreReviewConstants.REQUEST_KEY_REUSED_MESSAGE, null);
        }
        if (decision.outcome() == IdempotencyOutcome.REPLAY) {
            ScoreSourceAuditResponse stored = jsonMapper.readValue(decision.resultJson(),
                    ScoreSourceAuditResponse.class);
            return toResult(stored, true);
        }
        if (isPublicationLocked(tenantId, sessionPublicId)) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK,
                    ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE, null);
        }
        List<AiEvaluation> lockedAnswers = evaluationRepository.findSessionAnswersForUpdate(sessionPublicId, tenantId);
        Map<UUID, ExaminerEvaluation> examinerScores = submittedByAnswer(tenantId, sessionPublicId);
        Map<UUID, ScoreSelectionReader.Selection> selections = selectionReader.forSession(sessionPublicId, tenantId);
        String currentVersion = readQueryService.reviewVersion(lockedAnswers, examinerScores, selections);
        if (!Objects.equals(currentVersion, request.expectedReviewVersion())) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.STALE_REVIEW,
                    ScoreReviewConstants.STALE_REVIEW_MESSAGE, null);
        }
        ScoreSourceSelectionPreviewResponse preview = previewFor(tenantId, sessionPublicId, request, lockedAnswers,
                examinerScores, selections);
        if (preview.matchedAnswerCount() == 0 || preview.unavailableAnswerCount() > 0) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.SOURCE_UNAVAILABLE,
                    ScoreReviewConstants.SOURCE_UNAVAILABLE_MESSAGE, preview);
        }
        List<AiEvaluation> matching = matchingAnswers(lockedAnswers, request);
        Instant now = clock.instant();
        int previousAi = countWithSource(matching, selections, ScoreSource.AI);
        int previousExaminer = countWithSource(matching, selections, ScoreSource.EXAMINER);
        int previousUnselected = matching.size() - previousAi - previousExaminer;
        List<AiEvaluation> changed = matching.stream()
                .filter(answer -> ScoreSelectionReader.sourceOf(selections, answer.getAnswerPublicId())
                        != request.selectedSource()).toList();
        Map<UUID, UUID> evaluationByAnswer = new HashMap<>();
        for (AiEvaluation answer : changed) {
            ExaminerEvaluation examiner = examinerScores.get(answer.getAnswerPublicId());
            evaluationByAnswer.put(answer.getAnswerPublicId(),
                    request.selectedSource() == ScoreSource.EXAMINER ? examiner.getPublicId() : answer.getPublicId());
        }
        attemptService.selectScoreSource(changed.stream().map(AiEvaluation::getAnswerPublicId).toList(), tenantId,
                request.selectedSource().name(), caller.userId(), now, evaluationByAnswer);
        ScoreSourceAuditResponse audit = new ScoreSourceAuditResponse(UUID.randomUUID(), request.requestPublicId(),
                caller.userId(), request.scope(), normalizedScopeValue(request), request.selectedSource(),
                changed.size(), previousAi, previousExaminer, previousUnselected, now);
        String auditJson = jsonMapper.writeValueAsString(audit);
        auditWriter.record(caller, new AuditChange(ScoreReviewConstants.AUDIT_AGGREGATE_TYPE,
                sessionPublicId.toString(), ScoreReviewConstants.AUDIT_ACTION,
                ScoreReviewConstants.AUDIT_SUMMARY.formatted(request.selectedSource(), changed.size()),
                request.requestPublicId(), null, auditJson));
        idempotencyKeyService.complete(IdempotencyScope.SCORE_SELECTION, key, tenantId, auditJson);
        return toResult(audit, false);
    }

    private ScoreSourceSelectionPreviewResponse previewFor(UUID tenantId, UUID sessionPublicId,
            SelectScoreSourceRequest request, List<AiEvaluation> answers,
            Map<UUID, ExaminerEvaluation> examinerScores, Map<UUID, ScoreSelectionReader.Selection> selections) {
        List<AiEvaluation> matching = matchingAnswers(answers, request);
        int available = (int) matching.stream().filter(answer -> isAvailable(answer,
                examinerScores.get(answer.getAnswerPublicId()), request.selectedSource())).count();
        int currentAi = countWithSource(matching, selections, ScoreSource.AI);
        int currentExaminer = countWithSource(matching, selections, ScoreSource.EXAMINER);
        boolean locked = isPublicationLocked(tenantId, sessionPublicId);
        String version = readQueryService.reviewVersion(answers, examinerScores, selections);
        return new ScoreSourceSelectionPreviewResponse(version, request.scope(), normalizedScopeValue(request),
                request.selectedSource(), matching.size(), available, matching.size() - available,
                currentAi, currentExaminer, matching.size() - currentAi - currentExaminer,
                locked, !locked && !matching.isEmpty() && available == matching.size());
    }

    private int countWithSource(List<AiEvaluation> answers, Map<UUID, ScoreSelectionReader.Selection> selections,
            ScoreSource source) {
        return (int) answers.stream()
                .filter(answer -> ScoreSelectionReader.sourceOf(selections, answer.getAnswerPublicId()) == source)
                .count();
    }

    private Map<UUID, ExaminerEvaluation> submittedByAnswer(UUID tenantId, UUID sessionPublicId) {
        return examinerRepository.findSubmittedBySession(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerEvaluation::getAnswerPublicId, Function.identity()));
    }

    private List<AiEvaluation> matchingAnswers(List<AiEvaluation> answers, SelectScoreSourceRequest request) {
        return answers.stream().filter(this::isAiEligible).filter(answer -> switch (request.scope()) {
            case ALL -> true;
            case SECTION -> answer.getSection() != null
                    && answer.getSection().equalsIgnoreCase(request.scopeValue().trim());
            case TASK_TYPE -> TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.getTaskType())
                    .equals(TaskTypeCodeCompatibility.normalizeTaskTypeKey(request.scopeValue()));
        }).toList();
    }

    private boolean isAiEligible(AiEvaluation answer) {
        return answer.getScoringMethod() != null && answer.getScoringMethod().isAiScored();
    }

    private boolean isAvailable(AiEvaluation answer, ExaminerEvaluation examinerScore, ScoreSource source) {
        return source == ScoreSource.AI ? answer.hasPublishableAiScore()
                : examinerScore != null && examinerScore.isPublishable();
    }

    private boolean isPublicationLocked(UUID tenantId, UUID sessionPublicId) {
        return gateway.isPublished(tenantId, sessionPublicId);
    }

    private String fingerprint(SelectScoreSourceRequest request, UUID actorPublicId) {
        return actorPublicId + "|" + request.scope() + "|" + normalizedScopeValue(request) + "|"
                + request.selectedSource();
    }

    private ScoreSourceSelectionResultResponse toResult(ScoreSourceAuditResponse audit, boolean replayed) {
        return new ScoreSourceSelectionResultResponse(audit.auditPublicId(), audit.requestPublicId(),
                audit.scope(), audit.scopeValue(), audit.selectedSource(), audit.affectedAnswerCount(),
                audit.previousAiCount(), audit.previousExaminerCount(), audit.previousUnselectedCount(),
                audit.occurredAt(), replayed);
    }

    private String normalizedScopeValue(SelectScoreSourceRequest request) {
        return request.scope() == ScoreSourceSelectionScope.ALL ? null : request.scopeValue().trim();
    }

    private void validateRequest(SelectScoreSourceRequest request, boolean applying) {
        if (request == null || request.scope() == null || request.selectedSource() == null
                || request.scope() != ScoreSourceSelectionScope.ALL
                    && (request.scopeValue() == null || request.scopeValue().isBlank())
                || request.scope() == ScoreSourceSelectionScope.ALL && request.scopeValue() != null
                || applying && (request.requestPublicId() == null || request.expectedReviewVersion() == null
                    || request.expectedReviewVersion().isBlank())) {
            throw error(HttpStatus.BAD_REQUEST, ScoreReviewConstants.INVALID_SELECTION,
                    ScoreReviewConstants.SCOPE_REQUIRED_MESSAGE, null);
        }
    }

    private UUID requireHost(CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || !caller.hasRole("HOST_ADMIN")) {
            throw error(HttpStatus.FORBIDDEN, ScoreReviewConstants.INVALID_SELECTION,
                    ScoreReviewConstants.SCOPE_REQUIRED_MESSAGE, null);
        }
        return caller.tenantId();
    }

    private ScoreSourceSelectionException error(HttpStatus status, String code, String message, Object data) {
        return new ScoreSourceSelectionException(status, code, data, message);
    }

    private void assertNotManualExaminer(UUID tenantId, UUID sessionPublicId) {
        gateway.grading(tenantId, sessionPublicId)
                .ifPresent(grading -> {
                    if (grading.markingMode() == GradingMarkingMode.MANUAL_EXAMINER) {
                        throw error(HttpStatus.CONFLICT, ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED,
                                ScoreReviewConstants.MANUAL_EXAMINER_SELECTION_PROHIBITED_MESSAGE, null);
                    }
                });
    }
}
