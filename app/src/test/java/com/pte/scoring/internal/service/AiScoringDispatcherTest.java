package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.config.AiProviderProperties;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Since Phase 4 (plans/score-template-exam-generation): the caller
 * ({@code ScoringCommandService}) already resolved {@code scoringMethod}
 * via {@code ScoringMethodResolver} before calling {@code supports}/{@code
 * dispatch} — no more hardcoded-catalog lookup inside this class.
 */
@ExtendWith(MockitoExtension.class)
class AiScoringDispatcherTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiScoringDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AiScoringDispatcher(scoringAnswerRepository, rabbitTemplate,
                Clock.fixed(NOW, ZoneOffset.UTC), new AiProviderProperties());
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void dispatch_marksAnswerInFlightAndPublishesOnlyIdentifiersAndToken() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);

        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(answer.getAiDispatchedAt()).isEqualTo(NOW);
        verify(scoringAnswerRepository).save(answer);
        AiScoringJob job = sentJob();
        assertThat(job.answerPublicId()).isEqualTo(answer.getAnswerPublicId());
        assertThat(job.attemptPublicId()).isEqualTo(answer.getAttemptPublicId());
        assertThat(job.sessionPublicId()).isEqualTo(answer.getSessionPublicId());
        assertThat(job.tenantId()).isEqualTo(answer.getTenantId());
        assertThat(job.scoringMethod()).isEqualTo("AI_SPEECH");
        assertThat(job.dispatchToken()).isEqualTo(answer.getDispatchToken());
        // The candidate's answer stays in the database: nothing in the message, DLQ or listener error log.
        assertThat(AiScoringJob.class.getRecordComponents()).extracting(RecordComponent::getName)
                .containsExactly("answerPublicId", "attemptPublicId", "sessionPublicId", "tenantId",
                        "scoringMethod", "dispatchToken");
    }

    @Test
    void dispatch_issuesANewTokenOnEveryDispatch() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);

        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);
        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);

        ArgumentCaptor<AiScoringJob> jobs = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate, times(2)).convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY), jobs.capture());
        assertThat(jobs.getAllValues()).extracting(AiScoringJob::dispatchToken).doesNotHaveDuplicates()
                .last().isEqualTo(answer.getDispatchToken());
    }

    @Test
    void dispatch_aiTextScoringMethod_embedsAiTextInJob() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_WRITE_ESSAY);

        dispatcher.dispatch(answer, ScoringMethod.AI_TEXT);

        assertThat(sentJob().scoringMethod()).isEqualTo("AI_TEXT");
    }

    @Test
    void dispatch_insideTransaction_sendsOnlyAfterCommit() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);

        verifyNoInteractions(rabbitTemplate);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertThat(sentJob().dispatchToken()).isEqualTo(answer.getDispatchToken());
    }

    @Test
    void dispatch_rolledBackTransaction_sendsNothing() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        TransactionSynchronizationManager.initSynchronization();

        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verifyNoInteractions(rabbitTemplate);
    }

    @Test
    void dispatch_brokerFailureLeavesTheRowForAStaleRedispatch() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_READ_ALOUD);
        doThrow(new AmqpException("broker unavailable")).when(rabbitTemplate)
                .convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE), eq(ScoringConstants.AI_SCORING_ROUTING_KEY),
                        any(AiScoringJob.class));

        dispatcher.dispatch(answer, ScoringMethod.AI_SPEECH);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
    }

    @Test
    void dispatch_rejectsNonAiScoringMethodBeforeChangingState() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION);

        assertThatThrownBy(() -> dispatcher.dispatch(answer, ScoringMethod.UNSCORED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        assertThat(answer.getDispatchToken()).isZero();
        verifyNoInteractions(scoringAnswerRepository, rabbitTemplate);
    }

    @Test
    void staleBefore_isTheConfiguredLimitBeforeNow() {
        Duration limit = Duration.ofMinutes(new AiProviderProperties().getStaleDispatchMinutes());

        assertThat(dispatcher.staleBefore()).isEqualTo(NOW.minus(limit));
    }

    @Test
    void supports_onlyAiSpeechAndAiTextScoringMethods() {
        assertThat(dispatcher.supports(null)).isFalse();
        assertThat(dispatcher.supports(ScoringMethod.OBJECTIVE)).isFalse();
        assertThat(dispatcher.supports(ScoringMethod.UNSCORED)).isFalse();
        assertThat(dispatcher.supports(ScoringMethod.AI_SPEECH)).isTrue();
        assertThat(dispatcher.supports(ScoringMethod.AI_TEXT)).isTrue();
    }

    private AiScoringJob sentJob() {
        ArgumentCaptor<AiScoringJob> jobCaptor = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate).convertAndSend(eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY), jobCaptor.capture());
        return jobCaptor.getValue();
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
        return answer;
    }
}
