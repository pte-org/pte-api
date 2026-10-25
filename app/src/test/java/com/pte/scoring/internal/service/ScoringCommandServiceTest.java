package com.pte.scoring.internal.service;

import com.pte.scoring.internal.service.ai.AiScoringDispatcher;
import com.pte.scoring.internal.service.objective.AnswerContentReader;
import com.pte.scoring.internal.service.objective.ObjectiveScoringService;
import com.pte.scoring.internal.service.selection.ScorePublicationLockService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoreReviewConstants;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.exception.ScoreSourceSelectionException;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import com.pte.scoring.support.EvaluationFixtures;
import com.pte.scoring.support.SubmittedAnswerFixtures;
import com.pte.session.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The scoring method of each answer is persisted on its evaluation when it is opened (read from the exam version
 * item), so the command only routes: objective answers are scored in place, AI answers are dispatched, and an
 * answer without a method stays pending.
 */
@ExtendWith(MockitoExtension.class)
class ScoringCommandServiceTest {

    @Mock
    private ScoringIngestService scoringIngestService;
    @Mock
    private AiEvaluationRepository evaluationRepository;
    @Mock
    private ObjectiveScoringService objectiveScoringService;
    @Mock
    private AiScoringDispatcher aiScoringDispatcher;
    @Mock
    private AnswerContentReader contentReader;
    @Mock
    private SessionService sessionService;
    @Mock
    private ScorePublicationLockService publicationLockService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private ScoringCommandService service;

    @BeforeEach
    void setUp() {
        service = new ScoringCommandService(scoringIngestService, evaluationRepository, objectiveScoringService,
                aiScoringDispatcher, contentReader, sessionService, publicationLockService, eventPublisher, java.time.Clock.systemUTC());
    }

    @Test
    void requestScoring_locksTheSessionThenIngestsBeforeScoringPendingAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any())).thenReturn(List.of());

        service.requestScoring(sessionPublicId, tenantId);

        var order = org.mockito.Mockito.inOrder(sessionService, publicationLockService, scoringIngestService);
        order.verify(sessionService).lockForScoreReviewMutation(sessionPublicId, tenantId);
        order.verify(publicationLockService).assertNotPublished(tenantId, sessionPublicId);
        order.verify(scoringIngestService).ingestForSession(sessionPublicId, tenantId);
    }

    @Test
    void requestScoring_rejectsWorkAfterPublicationBeforeIngest() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        doThrow(new ScoreSourceSelectionException(HttpStatus.CONFLICT, ScoreReviewConstants.PUBLISHED_LOCK,
                null, ScoreReviewConstants.PUBLISHED_LOCK_MESSAGE))
                .when(publicationLockService).assertNotPublished(tenantId, sessionPublicId);

        assertThatThrownBy(() -> service.requestScoring(sessionPublicId, tenantId))
                .isInstanceOf(ScoreSourceSelectionException.class);

        verify(sessionService).lockForScoreReviewMutation(sessionPublicId, tenantId);
        verify(scoringIngestService, never()).ingestForSession(sessionPublicId, tenantId);
    }

    @Test
    void requestScoring_scoresObjectiveAnswersSynchronouslyFromTheAttemptContent() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE, ScoringMethod.OBJECTIVE);
        SubmittedAnswerView content = SubmittedAnswerFixtures.answer(objectiveAnswer.getTaskType(), "[]", "1");
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any()))
                .thenReturn(List.of(objectiveAnswer));
        when(contentReader.forSession(sessionPublicId, tenantId))
                .thenReturn(Map.of(objectiveAnswer.getAnswerPublicId(), content));
        when(objectiveScoringService.supports(ScoringMethod.OBJECTIVE)).thenReturn(true);
        when(objectiveScoringService.score(content)).thenReturn(85);
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(85);
        verify(evaluationRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void requestScoring_dispatchesAiAnswersWithoutSavingTwiceOrReadingContent() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation aiAnswer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringMethod.AI_SPEECH);
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any()))
                .thenReturn(List.of(aiAnswer));
        when(objectiveScoringService.supports(ScoringMethod.AI_SPEECH)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.AI_SPEECH)).thenReturn(true);

        service.requestScoring(sessionPublicId, tenantId);

        verify(aiScoringDispatcher).dispatch(aiAnswer, ScoringMethod.AI_SPEECH);
        // Dispatch owns the save, and the worker reads the content itself.
        verify(evaluationRepository, never()).save(any());
        verify(contentReader, never()).forSession(any(), any());
    }

    @Test
    void requestScoring_redispatchesFailedAndStaleAiAnswersPickedWithTheDispatchersCutoff() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant staleBefore = Instant.parse("2026-10-11T09:50:00Z");
        AiEvaluation failed = answer(ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringMethod.AI_SPEECH);
        failed.setStatus(ScoringAnswerStatus.SCORING_FAILED);
        AiEvaluation stale = answer(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE, ScoringMethod.AI_SPEECH);
        stale.setStatus(ScoringAnswerStatus.AI_SCORING);
        when(aiScoringDispatcher.staleBefore()).thenReturn(staleBefore);
        when(evaluationRepository.findScoringWork(sessionPublicId, tenantId, staleBefore))
                .thenReturn(List.of(failed, stale));
        when(objectiveScoringService.supports(ScoringMethod.AI_SPEECH)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.AI_SPEECH)).thenReturn(true);

        service.requestScoring(sessionPublicId, tenantId);

        verify(aiScoringDispatcher).dispatch(failed, ScoringMethod.AI_SPEECH);
        verify(aiScoringDispatcher).dispatch(stale, ScoringMethod.AI_SPEECH);
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void requestScoring_unscoredScoringMethod_leavesAnswerPending() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation unscoredAnswer = answer(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION,
                ScoringMethod.UNSCORED);
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any()))
                .thenReturn(List.of(unscoredAnswer));
        when(objectiveScoringService.supports(ScoringMethod.UNSCORED)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.UNSCORED)).thenReturn(false);

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(unscoredAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(unscoredAnswer.getRawScore()).isNull();
        verify(objectiveScoringService, never()).score(any());
        verify(aiScoringDispatcher, never()).dispatch(any(), any());
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void requestScoring_answerWithoutAScoringMethod_staysPendingWithoutCallingEitherScorer() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation unknownAnswer = answer("UNKNOWN_TASK_TYPE", null);
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any()))
                .thenReturn(List.of(unknownAnswer));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(unknownAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verify(objectiveScoringService, never()).supports(any());
        verify(aiScoringDispatcher, never()).supports(any());
        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void requestScoring_processesMultipleAnswersOfDifferentTypes() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        AiEvaluation objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE, ScoringMethod.OBJECTIVE);
        AiEvaluation aiAnswer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD, ScoringMethod.AI_SPEECH);
        SubmittedAnswerView content = SubmittedAnswerFixtures.answer(objectiveAnswer.getTaskType(), "[]", "1");
        when(evaluationRepository.findScoringWork(eq(sessionPublicId), eq(tenantId), any()))
                .thenReturn(List.of(objectiveAnswer, aiAnswer));
        when(contentReader.forSession(sessionPublicId, tenantId))
                .thenReturn(Map.of(objectiveAnswer.getAnswerPublicId(), content));
        when(objectiveScoringService.supports(ScoringMethod.OBJECTIVE)).thenReturn(true);
        when(objectiveScoringService.supports(ScoringMethod.AI_SPEECH)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.AI_SPEECH)).thenReturn(true);
        when(objectiveScoringService.score(content)).thenReturn(90);
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(90);
        verify(evaluationRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher).dispatch(aiAnswer, ScoringMethod.AI_SPEECH);
    }

    private AiEvaluation answer(String taskType, ScoringMethod method) {
        return EvaluationFixtures.ai(taskType, method);
    }
}
