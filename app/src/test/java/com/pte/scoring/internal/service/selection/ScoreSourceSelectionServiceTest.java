package com.pte.scoring.internal.service.selection;

import com.pte.scoring.internal.service.grading.SessionGradingGateway;
import com.pte.scoring.internal.service.review.ScoringReviewReadQueryService;
import com.pte.attempt.AttemptService;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.ExaminerEvaluation;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.dto.response.ScoreSourceAuditResponse;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.internal.repository.ExaminerEvaluationRepository;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.session.SessionService;
import com.pte.shared.audit.AuditChange;
import com.pte.shared.audit.AuditHistoryEntry;
import com.pte.shared.audit.AuditHistoryReader;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.idempotency.IdempotencyDecision;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.idempotency.IdempotencyOutcome;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.web.PageMeta;
import com.pte.shared.web.PagedResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A host's bulk source choice: the choice itself is written on the attempt answers, the receipt lives in the shared
 * idempotency table and each applied choice is a row of the audit trail.
 */
@ExtendWith(MockitoExtension.class)
class ScoreSourceSelectionServiceTest {

    private static final String REVIEW_VERSION = "review-version-1";
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final JsonMapper jsonMapper = JsonMapper.builder().findAndAddModules().build();

    @Mock
    private SessionService sessionService;
    @Mock
    private AiEvaluationRepository evaluationRepository;
    @Mock
    private ExaminerEvaluationRepository examinerRepository;
    @Mock
    private SessionGradingGateway gateway;
    @Mock
    private ScoringReviewReadQueryService readQueryService;
    @Mock
    private ScoreSelectionReader selectionReader;
    @Mock
    private AttemptService attemptService;
    @Mock
    private IdempotencyKeyService idempotencyKeyService;
    @Mock
    private AuditLogChangeWriter auditWriter;
    @Mock
    private AuditHistoryReader auditReader;

    private ScoreSourceSelectionService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new ScoreSourceSelectionService(sessionService, evaluationRepository, examinerRepository,
                gateway, readQueryService, selectionReader, attemptService,
                idempotencyKeyService, auditWriter, auditReader, jsonMapper, java.time.Clock.systemUTC());
        host = new CurrentUser(actorId, tenantId, List.of("HOST_ADMIN"));
        lenient().when(selectionReader.forSession(sessionId, tenantId)).thenReturn(Map.of());
    }

    @Test
    void preview_sectionScopeCountsOnlyMatchingAiEligibleAnswers() {
        AiEvaluation speaking = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        AiEvaluation writing = answer("WRITE_ESSAY", ScoringMethod.AI_TEXT, "WRITING");
        AiEvaluation objective = answer("MC_READING_SINGLE", ScoringMethod.OBJECTIVE, "READING");
        when(evaluationRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId))
                .thenReturn(List.of(speaking, writing, objective));
        when(readQueryService.reviewVersion(any(), any(), any())).thenReturn(REVIEW_VERSION);
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.SECTION,
                "SPEAKING", ScoreSource.AI, null, null);

        var preview = service.preview(sessionId, request, host);

        assertThat(preview.matchedAnswerCount()).isOne();
        assertThat(preview.availableAnswerCount()).isOne();
        assertThat(preview.unavailableAnswerCount()).isZero();
        assertThat(preview.canApply()).isTrue();
    }

    @Test
    void preview_distinguishesUnavailableExaminerSourceFromZeroScore() {
        AiEvaluation answer = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        when(evaluationRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId)).thenReturn(List.of(answer));
        when(readQueryService.reviewVersion(any(), any(), any())).thenReturn(REVIEW_VERSION);

        var preview = service.preview(sessionId,
                new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null, ScoreSource.EXAMINER, null, null),
                host);

        assertThat(preview.matchedAnswerCount()).isOne();
        assertThat(preview.availableAnswerCount()).isZero();
        assertThat(preview.unavailableAnswerCount()).isOne();
        assertThat(preview.canApply()).isFalse();
    }

    @Test
    void apply_selectsOnTheAttemptAnswersAndRecordsTheChoiceAsReceiptAndAudit() {
        AiEvaluation answer = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        UUID requestId = UUID.randomUUID();
        stubNewRequest(requestId, List.of(answer));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        var result = service.apply(sessionId, request, host);

        assertThat(result.selectedSource()).isEqualTo(ScoreSource.AI);
        assertThat(result.affectedAnswerCount()).isOne();
        assertThat(result.previousUnselectedCount()).isOne();
        assertThat(result.replayed()).isFalse();
        verify(sessionService).lockForScoreReviewMutation(sessionId, tenantId);
        verify(attemptService).selectScoreSource(eq(List.of(answer.getAnswerPublicId())), eq(tenantId), eq("AI"),
                eq(actorId), any(Instant.class), eq(Map.of(answer.getAnswerPublicId(), answer.getPublicId())));
        ArgumentCaptor<AuditChange> audit = ArgumentCaptor.forClass(AuditChange.class);
        verify(auditWriter).record(eq(host), audit.capture());
        assertThat(audit.getValue().aggregateType()).isEqualTo(ScoreReviewConstants.AUDIT_AGGREGATE_TYPE);
        assertThat(audit.getValue().aggregateId()).isEqualTo(sessionId.toString());
        assertThat(audit.getValue().requestPublicId()).isEqualTo(requestId);
        verify(idempotencyKeyService).complete(eq(IdempotencyScope.SCORE_SELECTION), any(), eq(tenantId),
                eq(audit.getValue().afterStateJson()));
    }

    @Test
    void apply_selectingExaminerPointsTheAnswerAtTheSubmittedExaminerScore() {
        AiEvaluation answer = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        ExaminerEvaluation examiner = ExaminerEvaluation.submittedDirectly(tenantId, sessionId,
                answer.getAttemptPublicId(), answer.getAnswerPublicId(), UUID.randomUUID(), 70, Instant.now(), null,
                null, null, null, null, null);
        ReflectionTestUtils.setField(examiner, "publicId", UUID.randomUUID());
        UUID requestId = UUID.randomUUID();
        stubNewRequest(requestId, List.of(answer));
        when(examinerRepository.findSubmittedBySession(tenantId, sessionId)).thenReturn(List.of(examiner));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.EXAMINER, REVIEW_VERSION, requestId);

        service.apply(sessionId, request, host);

        verify(attemptService).selectScoreSource(eq(List.of(answer.getAnswerPublicId())), eq(tenantId),
                eq("EXAMINER"), eq(actorId), any(Instant.class),
                eq(Map.of(answer.getAnswerPublicId(), examiner.getPublicId())));
    }

    @Test
    void apply_rejectsStalePreviewWithoutMutation() {
        AiEvaluation answer = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        UUID requestId = UUID.randomUUID();
        stubNewRequest(requestId, List.of(answer));
        when(readQueryService.reviewVersion(any(), any(), any())).thenReturn("new-version");
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(attemptService, never()).selectScoreSource(any(), any(), any(), any(), any(), anyMap());
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void apply_rejectsUnavailableScoreAtomically() {
        AiEvaluation answer = answer("READ_ALOUD", ScoringMethod.AI_SPEECH, "SPEAKING");
        UUID requestId = UUID.randomUUID();
        stubNewRequest(requestId, List.of(answer));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.EXAMINER, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(attemptService, never()).selectScoreSource(any(), any(), any(), any(), any(), anyMap());
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void apply_replaysTheStoredReceiptOfTheSameRequestWithoutMutating() {
        UUID requestId = UUID.randomUUID();
        ScoreSourceAuditResponse prior = new ScoreSourceAuditResponse(UUID.randomUUID(), requestId, actorId,
                ScoreSourceSelectionScope.ALL, null, ScoreSource.AI, 1, 0, 0, 1, Instant.now());
        when(idempotencyKeyService.begin(eq(IdempotencyScope.SCORE_SELECTION), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.REPLAY, jsonMapper.writeValueAsString(prior)));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        var result = service.apply(sessionId, request, host);

        assertThat(result.replayed()).isTrue();
        assertThat(result.auditPublicId()).isEqualTo(prior.auditPublicId());
        verify(evaluationRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void apply_rejectsARequestKeyReusedForADifferentChoice() {
        UUID requestId = UUID.randomUUID();
        when(idempotencyKeyService.begin(eq(IdempotencyScope.SCORE_SELECTION), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.CONFLICT, null));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOfSatisfying(ScoreSourceSelectionException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(ScoreReviewConstants.REQUEST_KEY_REUSED));

        verify(evaluationRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
    }

    @Test
    void apply_rejectsMutationAfterPublicationBarrier() {
        UUID requestId = UUID.randomUUID();
        when(idempotencyKeyService.begin(eq(IdempotencyScope.SCORE_SELECTION), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
        when(gateway.isPublished(tenantId, sessionId)).thenReturn(true);
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(evaluationRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
        verify(auditWriter, never()).record(any(), any());
    }

    @Test
    void auditHistoryReadsTheRecordedDecisionsFromTheAuditTrail() {
        ScoreSourceAuditResponse recorded = new ScoreSourceAuditResponse(UUID.randomUUID(), UUID.randomUUID(),
                actorId, ScoreSourceSelectionScope.SECTION, "SPEAKING", ScoreSource.EXAMINER, 3, 2, 0, 1,
                Instant.parse("2026-10-24T08:00:00Z"));
        AuditHistoryEntry row = new AuditHistoryEntry(UUID.randomUUID(), actorId, "HOST_ADMIN",
                recorded.requestPublicId(), "summary", null, jsonMapper.writeValueAsString(recorded), Instant.now());
        when(auditReader.history(tenantId, ScoreReviewConstants.AUDIT_AGGREGATE_TYPE, sessionId.toString(),
                ScoreReviewConstants.AUDIT_ACTION, 0, 50)).thenReturn(new PagedResult<>(List.of(row),
                new PageMeta(0, 50, 1, 1, true, true, false, false)));

        var history = service.auditHistory(sessionId, host);

        assertThat(history).containsExactly(recorded);
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
    }

    /** A first-time request on an unpublished session whose review version matches. */
    private void stubNewRequest(UUID requestId, List<AiEvaluation> answers) {
        when(idempotencyKeyService.begin(eq(IdempotencyScope.SCORE_SELECTION), any(), eq(tenantId), any(), any()))
                .thenReturn(new IdempotencyDecision(IdempotencyOutcome.NEW, null));
        when(evaluationRepository.findSessionAnswersForUpdate(sessionId, tenantId)).thenReturn(answers);
        lenient().when(readQueryService.reviewVersion(any(), any(), any())).thenReturn(REVIEW_VERSION);
    }

    private AiEvaluation answer(String taskType, ScoringMethod method, String section) {
        AiEvaluation answer = EvaluationFixtures.ai(tenantId, sessionId, UUID.randomUUID(), UUID.randomUUID(),
                taskType, method, section);
        answer.markAiScored(0, AiProviderCategory.REAL, "provider", "model", "v1", java.time.Instant.now());
        ReflectionTestUtils.setField(answer, "publicId", UUID.randomUUID());
        return answer;
    }
}
