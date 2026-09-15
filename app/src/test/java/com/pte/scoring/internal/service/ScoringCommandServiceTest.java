package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringCommandServiceTest {

    @Mock
    private ScoringIngestService scoringIngestService;
    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private ObjectiveScoringService objectiveScoringService;
    @Mock
    private AiScoringDispatcher aiScoringDispatcher;

    private ScoringCommandService service;

    @BeforeEach
    void setUp() {
        service = new ScoringCommandService(scoringIngestService, scoringAnswerRepository,
                objectiveScoringService, aiScoringDispatcher);
    }

    @Test
    void requestScoring_callsIngestFirstThenScoresPendingAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer objectiveAnswer = answer(ScoringConstants.TASK_TYPE_MC_READING_SINGLE);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(objectiveAnswer));
        when(objectiveScoringService.supports(ScoringConstants.TASK_TYPE_MC_READING_SINGLE)).thenReturn(true);
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
        when(objectiveScoringService.supports(ScoringConstants.TASK_TYPE_MC_READING_SINGLE)).thenReturn(true);
        when(objectiveScoringService.score(objectiveAnswer)).thenReturn(85);
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(85);
        verify(scoringAnswerRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher, never()).dispatch(any());
    }

    @Test
    void requestScoring_dispatchesAiAnswersWithoutSavingTwice() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer aiAnswer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(aiAnswer));
        when(objectiveScoringService.supports(ScoringConstants.TASK_TYPE_READ_ALOUD)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringConstants.TASK_TYPE_READ_ALOUD)).thenReturn(true);

        service.requestScoring(sessionPublicId, tenantId);

        verify(aiScoringDispatcher).dispatch(aiAnswer);
        // Should not call save for AI answers — dispatch owns the save
        verify(scoringAnswerRepository, never()).save(any());
    }

    @Test
    void requestScoring_leavesUnsupportedAnswersUntouched() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ScoringAnswer unsupportedAnswer = answer("UNKNOWN_TASK_TYPE");
        when(scoringAnswerRepository.findBySessionPublicIdAndTenantIdAndStatus(
                sessionPublicId, tenantId, ScoringAnswerStatus.PENDING))
                .thenReturn(List.of(unsupportedAnswer));
        when(objectiveScoringService.supports("UNKNOWN_TASK_TYPE")).thenReturn(false);
        when(aiScoringDispatcher.supports("UNKNOWN_TASK_TYPE")).thenReturn(false);

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(unsupportedAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(unsupportedAnswer.getRawScore()).isNull();
        verify(objectiveScoringService, never()).score(any());
        verify(aiScoringDispatcher, never()).dispatch(any());
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
        when(objectiveScoringService.supports(ScoringConstants.TASK_TYPE_MC_READING_SINGLE)).thenReturn(true);
        when(objectiveScoringService.supports(ScoringConstants.TASK_TYPE_READ_ALOUD)).thenReturn(false);
        when(aiScoringDispatcher.supports(ScoringConstants.TASK_TYPE_READ_ALOUD)).thenReturn(true);
        when(objectiveScoringService.score(objectiveAnswer)).thenReturn(90);
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.requestScoring(sessionPublicId, tenantId);

        assertThat(objectiveAnswer.getStatus()).isEqualTo(ScoringAnswerStatus.SCORED);
        assertThat(objectiveAnswer.getRawScore()).isEqualTo(90);
        verify(scoringAnswerRepository).save(objectiveAnswer);
        verify(aiScoringDispatcher).dispatch(aiAnswer);
    }

    private ScoringAnswer answer(String taskType) {
        ScoringAnswer answer = new ScoringAnswer();
        answer.setAnswerPublicId(UUID.randomUUID());
        answer.setAttemptPublicId(UUID.randomUUID());
        answer.setPinnedItemPublicId(UUID.randomUUID());
        answer.setSessionPublicId(UUID.randomUUID());
        answer.setTenantId(UUID.randomUUID());
        answer.setTaskType(taskType);
        answer.setPayload("payload");
        answer.setCorrectAnswerText("reference");
        answer.setStatus(ScoringAnswerStatus.PENDING);
        return answer;
    }
}
