package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Since Phase 4 (plans/score-template-exam-generation): {@code
 * scoringMethod} per answer comes from {@code ScoringMethodResolver}
 * (pinned ScoreTemplate), not from {@code ObjectiveScoringService}/{@code
 * AiScoringDispatcher} each independently deciding via a hardcoded task-type
 * set.
 */
@ExtendWith(MockitoExtension.class)
class ScoringCommandServiceTest {

    private static final UUID SCORE_TEMPLATE_ID = UUID.randomUUID();

    @Mock
    private ScoringIngestService scoringIngestService;
    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private ObjectiveScoringService objectiveScoringService;
    @Mock
    private AiScoringDispatcher aiScoringDispatcher;
    @Mock
    private ScoringMethodResolver scoringMethodResolver;

    private ScoringCommandService service;

    @BeforeEach
    void setUp() {
        service = new ScoringCommandService(scoringIngestService, scoringAnswerRepository,
                objectiveScoringService, aiScoringDispatcher, scoringMethodResolver);
    }

    @Test
    void requestScoring_callsIngestFirstThenScoresPendingAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(objectiveAnswer));
        stubMethod(objectiveAnswer, ScoringMethod.OBJECTIVE);
        when(objectiveScoringService.supports(ScoringMethod.OBJECTIVE)).thenReturn(true);
        when(objectiveScoringService.score(objectiveAnswer)).thenReturn(85);

        service.requestScoring(sessionPublicId, tenantId);

        verify(scoringIngestService).ingestForSession(sessionPublicId, tenantId);
    }

    @Test
    void requestScoring_scoresObjectiveAnswersSynchronously() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(objectiveAnswer));
        stubMethod(objectiveAnswer, ScoringMethod.OBJECTIVE);
        when(objectiveScoringService.supports(ScoringMethod.OBJECTIVE)).thenReturn(true);
        when(objectiveScoringService.score(objectiveAnswer)).thenReturn(85);
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(85);
        verify(scoringAnswerRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher, never()).dispatch(any(), any());
    }

    @Test
    void requestScoring_dispatchesAiAnswersWithoutSavingTwice() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer aiAnswer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(aiAnswer));
        stubMethod(aiAnswer, ScoringMethod.AI_SPEECH);
        when(objectiveScoringService.supports(ScoringMethod.AI_SPEECH)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.AI_SPEECH)).thenReturn(true);

        service.requestScoring(sessionPublicId, tenantId);

        verify(aiScoringDispatcher).dispatch(aiAnswer, ScoringMethod.AI_SPEECH);
        // Should not call save for AI answers — dispatch owns the save
        verify(scoringAnswerRepository, never()).save(any());
    }

    @Test
    void requestScoring_unscoredScoringMethod_leavesAnswerPending() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer unscoredAnswer = answer(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(unscoredAnswer));
        stubMethod(unscoredAnswer, ScoringMethod.UNSCORED);
        when(objectiveScoringService.supports(ScoringMethod.UNSCORED)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.UNSCORED)).thenReturn(false);

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(unscoredAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(unscoredAnswer.getRawScore()).isNull();
        verify(objectiveScoringService, never()).score(any());
        verify(aiScoringDispatcher, never()).dispatch(any(), any());
        verify(scoringAnswerRepository, never()).save(any());
    }

    @Test
    void requestScoring_taskTypeNotInTemplate_leavesAnswerPendingWithoutCallingEitherScorer() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer unknownAnswer = answer("UNKNOWN_TASK_TYPE");
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(unknownAnswer));
        when(scoringMethodResolver.resolve(SCORE_TEMPLATE_ID, "UNKNOWN_TASK_TYPE")).thenReturn(Optional.empty());

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(unknownAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verify(objectiveScoringService, never()).supports(any());
        verify(aiScoringDispatcher, never()).supports(any());
        verify(scoringAnswerRepository, never()).save(any());
    }

    @Test
    void requestScoring_processesMultipleAnswersOfDifferentTypes() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE);
        ScoringAnswer aiAnswer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);

        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(objectiveAnswer, aiAnswer));
        stubMethod(objectiveAnswer, ScoringMethod.OBJECTIVE);
        stubMethod(aiAnswer, ScoringMethod.AI_SPEECH);
        when(objectiveScoringService.supports(ScoringMethod.OBJECTIVE)).thenReturn(true);
        when(objectiveScoringService.supports(ScoringMethod.AI_SPEECH)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringMethod.AI_SPEECH)).thenReturn(true);
        when(objectiveScoringService.score(objectiveAnswer)).thenReturn(90);
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(90);
        verify(scoringAnswerRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher).dispatch(aiAnswer, ScoringMethod.AI_SPEECH);
    }

    private void stubMethod(ScoringAnswer answer, ScoringMethod method) {
        when(scoringMethodResolver.resolve(SCORE_TEMPLATE_ID, answer.getTaskType())).thenReturn(Optional.of(method));
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setScoreTemplatePublicId(SCORE_TEMPLATE_ID);
        answer.setTaskType(taskType);
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        answer.setStatus(ScoringAnswerStatus.PENDING);
        return answer;
    }
}
