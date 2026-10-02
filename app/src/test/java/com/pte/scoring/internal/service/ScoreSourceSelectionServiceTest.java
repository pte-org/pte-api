package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ExaminerAnswerScore;
import com.pte.scoring.domain.ScoreSourceAudit;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.ScoringSessionState;
import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import com.pte.scoring.domain.enums.ScoreSourceSelectionScope;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.dto.request.SelectScoreSourceRequest;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.ExaminerAnswerScoreRepository;
import com.pte.scoring.internal.repository.ScoreSourceAuditRepository;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import com.pte.scoring.internal.repository.ScoringSessionStateRepository;
import com.pte.session.SessionService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoreSourceSelectionServiceTest {

    private static final String REVIEW_VERSION = "review-version-1";
    private final UUID tenantId = UUID.randomUUID();
    private final UUID sessionId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @Mock
    private SessionService sessionService;
    @Mock
    private ScoringAnswerRepository answerRepository;
    @Mock
    private ExaminerAnswerScoreRepository examinerScoreRepository;
    @Mock
    private ScoringSessionStateRepository sessionStateRepository;
    @Mock
    private ScoreSourceAuditRepository auditRepository;
    @Mock
    private ScoringMethodResolver methodResolver;
    @Mock
    private ScoringReviewReadQueryService readQueryService;

    private ScoreSourceSelectionService service;
    private CurrentUser host;

    @BeforeEach
    void setUp() {
        service = new ScoreSourceSelectionService(sessionService, answerRepository, examinerScoreRepository,
                sessionStateRepository, auditRepository, methodResolver, readQueryService);
        host = new CurrentUser(actorId, tenantId, List.of("HOST_ADMIN"));
    }

    @Test
    void preview_sectionScopeCountsOnlyMatchingAiEligibleAnswers() {
        ScoringAnswer speaking = answer("READ_ALOUD");
        ScoringAnswer writing = answer("WRITE_ESSAY");
        when(answerRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId))
                .thenReturn(List.of(speaking, writing));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(methodResolver.resolve(templateId, "WRITE_ESSAY")).thenReturn(Optional.of(ScoringMethod.AI_TEXT));
        when(methodResolver.resolveSection(templateId, "READ_ALOUD")).thenReturn(Optional.of("SPEAKING"));
        when(methodResolver.resolveSection(templateId, "WRITE_ESSAY")).thenReturn(Optional.of("WRITING"));
        when(readQueryService.reviewVersion(any(), any())).thenReturn(REVIEW_VERSION);
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
        ScoringAnswer answer = answer("READ_ALOUD");
        when(answerRepository.findBySessionPublicIdAndTenantId(sessionId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(readQueryService.reviewVersion(any(), any())).thenReturn(REVIEW_VERSION);

        var preview = service.preview(sessionId,
                new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null, ScoreSource.EXAMINER, null, null), host);

        assertThat(preview.matchedAnswerCount()).isOne();
        assertThat(preview.availableAnswerCount()).isZero();
        assertThat(preview.unavailableAnswerCount()).isOne();
        assertThat(preview.canApply()).isFalse();
    }

    @Test
    void apply_selectsAvailableSourceAndWritesAuditCounts() {
        ScoringAnswer answer = answer("READ_ALOUD");
        UUID requestId = UUID.randomUUID();
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.empty());
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(Optional.empty());
        when(answerRepository.findSessionAnswersForUpdate(sessionId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(readQueryService.reviewVersion(any(), any())).thenReturn(REVIEW_VERSION);
        when(auditRepository.saveAndFlush(any(ScoreSourceAudit.class))).thenAnswer(invocation -> {
            ScoreSourceAudit audit = invocation.getArgument(0);
            audit.setPublicId(UUID.randomUUID());
            return audit;
        });
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        var result = service.apply(sessionId, request, host);

        assertThat(answer.getSelectedScoreSource()).isEqualTo(ScoreSource.AI);
        assertThat(result.selectedSource()).isEqualTo(ScoreSource.AI);
        assertThat(result.affectedAnswerCount()).isOne();
        assertThat(result.previousUnselectedCount()).isOne();
        assertThat(result.replayed()).isFalse();
        verify(answerRepository).saveAll(List.of(answer));
        verify(sessionService).lockForScoreReviewMutation(sessionId, tenantId);
    }

    @Test
    void apply_rejectsStalePreviewWithoutMutation() {
        ScoringAnswer answer = answer("READ_ALOUD");
        UUID requestId = UUID.randomUUID();
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.empty());
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(Optional.empty());
        when(answerRepository.findSessionAnswersForUpdate(sessionId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(readQueryService.reviewVersion(any(), any())).thenReturn("new-version");
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(answerRepository, never()).saveAll(any());
        verify(auditRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_rejectsUnavailableScoreAtomically() {
        ScoringAnswer answer = answer("READ_ALOUD");
        UUID requestId = UUID.randomUUID();
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.empty());
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(Optional.empty());
        when(answerRepository.findSessionAnswersForUpdate(sessionId, tenantId)).thenReturn(List.of(answer));
        when(examinerScoreRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId)).thenReturn(List.of());
        when(methodResolver.resolve(templateId, "READ_ALOUD")).thenReturn(Optional.of(ScoringMethod.AI_SPEECH));
        when(readQueryService.reviewVersion(any(), any())).thenReturn(REVIEW_VERSION);
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.EXAMINER, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        assertThat(answer.getSelectedScoreSource()).isNull();
        verify(answerRepository, never()).saveAll(any());
        verify(auditRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_retriesSameRequestFromAuditWithoutDuplicatingMutation() {
        UUID requestId = UUID.randomUUID();
        ScoreSourceAudit prior = new ScoreSourceAudit(tenantId, sessionId, actorId, ScoreSourceSelectionScope.ALL,
                null, ScoreSource.AI, 1, requestId, 0, 0, 1, Instant.now());
        prior.setPublicId(UUID.randomUUID());
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.of(prior));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        var result = service.apply(sessionId, request, host);

        assertThat(result.replayed()).isTrue();
        assertThat(result.auditPublicId()).isEqualTo(prior.getPublicId());
        verify(answerRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
        verify(auditRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_rejectsRequestKeyPreviouslyUsedByAnotherHost() {
        UUID requestId = UUID.randomUUID();
        ScoreSourceAudit prior = new ScoreSourceAudit(tenantId, sessionId, UUID.randomUUID(),
                ScoreSourceSelectionScope.ALL, null, ScoreSource.AI, 1, requestId, 0, 0, 1, Instant.now());
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.of(prior));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(answerRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
        verify(auditRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_rejectsMutationAfterPublicationBarrier() {
        UUID requestId = UUID.randomUUID();
        ScoringSessionState state = new ScoringSessionState(tenantId, sessionId);
        state.lockForPublication(UUID.randomUUID(), Instant.now());
        when(auditRepository.findByTenantIdAndSessionPublicIdAndRequestPublicId(tenantId, sessionId, requestId))
                .thenReturn(Optional.empty());
        when(sessionStateRepository.findByTenantIdAndSessionPublicId(tenantId, sessionId))
                .thenReturn(Optional.of(state));
        SelectScoreSourceRequest request = new SelectScoreSourceRequest(ScoreSourceSelectionScope.ALL, null,
                ScoreSource.AI, REVIEW_VERSION, requestId);

        assertThatThrownBy(() -> service.apply(sessionId, request, host))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(answerRepository, never()).findSessionAnswersForUpdate(sessionId, tenantId);
        verify(auditRepository, never()).saveAndFlush(any());
    }

    @Test
    void auditHistoryReturnsTenantScopedRecordedDecisions() {
        ScoreSourceAudit audit = new ScoreSourceAudit(tenantId, sessionId, actorId,
                ScoreSourceSelectionScope.SECTION, "SPEAKING", ScoreSource.EXAMINER, 3,
                UUID.randomUUID(), 2, 0, 1, Instant.now());
        audit.setPublicId(UUID.randomUUID());
        when(auditRepository.findTop50ByTenantIdAndSessionPublicIdOrderByOccurredAtDesc(tenantId, sessionId))
                .thenReturn(List.of(audit));

        var history = service.auditHistory(sessionId, host);

        assertThat(history).hasSize(1);
        assertThat(history.get(0).auditPublicId()).isEqualTo(audit.getPublicId());
        assertThat(history.get(0).scopeValue()).isEqualTo("SPEAKING");
        assertThat(history.get(0).selectedSource()).isEqualTo(ScoreSource.EXAMINER);
        assertThat(history.get(0).previousAiCount()).isEqualTo(2);
        verify(sessionService).verifyHostAccess(sessionId, tenantId);
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(sessionId);
        answer.setTenantId(tenantId);
        answer.setScoreTemplatePublicId(templateId);
        answer.setTaskType(taskType);
        answer.markAiScored(0, AiProviderCategory.REAL, "provider", "model", "v1");
        return answer;
    }
}
