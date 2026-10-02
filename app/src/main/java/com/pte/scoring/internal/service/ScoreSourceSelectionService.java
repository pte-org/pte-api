package com.pte.scoring.internal.service;

import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ScoreSourceAudit;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.dto.response.ScoreSourceSelectionPreviewResponse;
import com.pte.scoring.dto.response.ScoreSourceSelectionResultResponse;
import com.pte.scoring.dto.response.ScoreSourceAuditResponse;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ScoreSourceAuditRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ScoreSourceSelectionService {

    private final SessionService sessionService;
    private final ScoringAnswerRepository answerRepository;
    private final ExaminerAnswerScoreRepository examinerScoreRepository;
    private final ScoringSessionStateRepository sessionStateRepository;
    private final ScoreSourceAuditRepository auditRepository;
    private final ScoringMethodResolver methodResolver;
    private final ScoringReviewReadQueryService readQueryService;

    public ScoreSourceSelectionService(SessionService sessionService,
            ScoringAnswerRepository answerRepository,
            ExaminerAnswerScoreRepository examinerScoreRepository,
            ScoringSessionStateRepository sessionStateRepository,
            ScoreSourceAuditRepository auditRepository,
            ScoringMethodResolver methodResolver,
            ScoringReviewReadQueryService readQueryService) {
        this.sessionService = sessionService;
        this.answerRepository = answerRepository;
        this.examinerScoreRepository = examinerScoreRepository;
        this.sessionStateRepository = sessionStateRepository;
        this.auditRepository = auditRepository;
        this.methodResolver = methodResolver;
        this.readQueryService = readQueryService;
    }

    @Transactional(readOnly = true)
    public ScoreSourceSelectionPreviewResponse preview(UUID sessionPublicId, SelectScoreSourceRequest request,
            CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        validateRequest(request, false);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        return previewFor(tenantId, sessionPublicId, request,
                answerRepository.findBySessionPublicIdAndTenantId(sessionPublicId, tenantId));
    }

    @Transactional(readOnly = true)
    public List<ScoreSourceAuditResponse> auditHistory(UUID sessionPublicId, CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        sessionService.verifyHostAccess(sessionPublicId, tenantId);
        return auditRepository.findTop50ByTenantIdAndSessionPublicIdOrderByOccurredAtDesc(tenantId, sessionPublicId)
                .stream()
                .map(audit -> new ScoreSourceAuditResponse(audit.getPublicId(), audit.getRequestPublicId(),
                        audit.getActorPublicId(), audit.getScope(), audit.getScopeValue(), audit.getSelectedSource(),
                        audit.getAffectedAnswerCount(), audit.getPreviousAiCount(), audit.getPreviousExaminerCount(),
                        audit.getPreviousUnselectedCount(), audit.getOccurredAt()))
                .toList();
    }

    @Transactional
    public ScoreSourceSelectionResultResponse apply(UUID sessionPublicId, SelectScoreSourceRequest request,
            CurrentUser caller) {
        UUID tenantId = requireHost(caller);
        validateRequest(request, true);
        sessionService.lockForScoreReviewMutation(sessionPublicId, tenantId);

        var prior = auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(
                tenantId, sessionPublicId, request.requestPublicId());
        if (prior.isPresent()) {
            ScoreSourceAudit audit = prior.get();
            if (!sameRequest(audit, request, caller.userId())) {
                throw error(HttpStatus.CONFLICT, ScoreReviewConstants.REQUEST_KEY_REUSED,
                        ScoreReviewConstants.REQUEST_KEY_REUSED_MESSAGE, null);
            }
            return toResult(audit, true);
        }

        if (isPublicationLocked(tenantId, sessionPublicId)) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK,
                    ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE, null);
        }

        List<ScoringAnswer> lockedAnswers = answerRepository.findSessionAnswersForUpdate(sessionPublicId, tenantId);
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        String currentVersion = readQueryService.reviewVersion(lockedAnswers, examinerScores);
        if (!Objects.equals(currentVersion, request.expectedReviewVersion())) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.STALE_REVIEW,
                    ScoreReviewConstants.STALE_REVIEW_MESSAGE, null);
        }

        ScoreSourceSelectionPreviewResponse preview = previewFor(tenantId, sessionPublicId, request, lockedAnswers,
                examinerScores);
        if (preview.matchedAnswerCount() == 0 || preview.unavailableAnswerCount() > 0) {
            throw error(HttpStatus.CONFLICT, ScoreReviewConstants.SOURCE_UNAVAILABLE,
                    ScoreReviewConstants.SOURCE_UNAVAILABLE_MESSAGE, preview);
        }

        List<ScoringAnswer> matching = matchingAnswers(lockedAnswers, request);
        Instant now = Instant.now();
        int previousAi = (int) matching.stream().filter(answer -> answer.getSelectedScoreSource() == ScoreSource.AI).count();
        int previousExaminer = (int) matching.stream()
                .filter(answer -> answer.getSelectedScoreSource() == ScoreSource.EXAMINER).count();
        int previousUnselected = matching.size() - previousAi - previousExaminer;
        List<ScoringAnswer> changed = matching.stream()
                .filter(answer -> answer.getSelectedScoreSource() != request.selectedSource()).toList();
        for (ScoringAnswer answer : changed) {
            ExaminerAnswerScore examinerScore = examinerScores.get(answer.getAnswerPublicId());
            answer.selectScoreSource(request.selectedSource(), caller.userId(), now, examinerScore);
        }
        answerRepository.saveAll(changed);
        ScoreSourceAudit audit = auditRepository.saveAndFlush(new ScoreSourceAudit(tenantId, sessionPublicId,
                caller.userId(), request.scope(), normalizedScopeValue(request), request.selectedSource(),
                changed.size(), request.requestPublicId(), previousAi, previousExaminer, previousUnselected, now));
        return toResult(audit, false);
    }

    private ScoreSourceSelectionPreviewResponse previewFor(UUID tenantId, UUID sessionPublicId,
            SelectScoreSourceRequest request, List<ScoringAnswer> answers) {
        Map<UUID, ExaminerAnswerScore> examinerScores = examinerScoreRepository
                .findByTenantIdAndSessionPublicId(tenantId, sessionPublicId).stream()
                .collect(Collectors.toMap(ExaminerAnswerScore::getAnswerPublicId, Function.identity()));
        return previewFor(tenantId, sessionPublicId, request, answers, examinerScores);
    }

    private ScoreSourceSelectionPreviewResponse previewFor(UUID tenantId, UUID sessionPublicId,
            SelectScoreSourceRequest request, List<ScoringAnswer> answers,
            Map<UUID, ExaminerAnswerScore> examinerScores) {
        List<ScoringAnswer> matching = matchingAnswers(answers, request);
        int available = (int) matching.stream().filter(answer -> isAvailable(answer,
                examinerScores.get(answer.getAnswerPublicId()), request.selectedSource())).count();
        int currentAi = (int) matching.stream().filter(answer -> answer.getSelectedScoreSource() == ScoreSource.AI).count();
        int currentExaminer = (int) matching.stream()
                .filter(answer -> answer.getSelectedScoreSource() == ScoreSource.EXAMINER).count();
        boolean locked = isPublicationLocked(tenantId, sessionPublicId);
        String version = readQueryService.reviewVersion(answers, examinerScores);
        return new ScoreSourceSelectionPreviewResponse(version, request.scope(), normalizedScopeValue(request),
                request.selectedSource(), matching.size(), available, matching.size() - available,
                currentAi, currentExaminer, matching.size() - currentAi - currentExaminer,
                locked, !locked && !matching.isEmpty() && available == matching.size());
    }

    private List<ScoringAnswer> matchingAnswers(List<ScoringAnswer> answers, SelectScoreSourceRequest request) {
        return answers.stream().filter(this::isAiEligible).filter(answer -> switch (request.scope()) {
            case ALL -> true;
            case SECTION -> methodResolver.resolveSection(answer.getScoreTemplatePublicId(), answer.getTaskType())
                    .map(section -> section.equalsIgnoreCase(request.scopeValue().trim())).orElse(false);
            case TASK_TYPE -> TaskTypeCodeCompatibility.normalizeTaskTypeKey(answer.getTaskType())
                    .equals(TaskTypeCodeCompatibility.normalizeTaskTypeKey(request.scopeValue()));
        }).toList();
    }

    private boolean isAiEligible(ScoringAnswer answer) {
        return methodResolver.resolve(answer.getScoreTemplatePublicId(), answer.getTaskType())
                .map(ScoringMethod::isAiScored).orElse(false);
    }

    private boolean isAvailable(ScoringAnswer answer, ExaminerAnswerScore examinerScore, ScoreSource source) {
        return source == ScoreSource.AI ? answer.hasPublishableAiScore()
                : examinerScore != null && examinerScore.isPublishable();
    }

    private boolean isPublicationLocked(UUID tenantId, UUID sessionPublicId) {
        return sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionPublicId)
                .map(ScoringSessionState::getPublicationPublicId).orElse(null) != null;
    }

    private boolean sameRequest(ScoreSourceAudit audit, SelectScoreSourceRequest request, UUID actorPublicId) {
        return audit.getActorPublicId().equals(actorPublicId)
                && audit.getScope() == request.scope()
                && Objects.equals(audit.getScopeValue(), normalizedScopeValue(request))
                && audit.getSelectedSource() == request.selectedSource();
    }

    private ScoreSourceSelectionResultResponse toResult(ScoreSourceAudit audit, boolean replayed) {
        return new ScoreSourceSelectionResultResponse(audit.getPublicId(), audit.getRequestPublicId(),
                audit.getScope(), audit.getScopeValue(), audit.getSelectedSource(), audit.getAffectedAnswerCount(),
                audit.getPreviousAiCount(), audit.getPreviousExaminerCount(), audit.getPreviousUnselectedCount(),
                audit.getOccurredAt(), replayed);
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
}
