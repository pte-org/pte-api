package com.pte.scoring.internal.service;

import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.messaging.job.AiScoringJob;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AiScoringDispatcherTest {

    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiScoringDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new AiScoringDispatcher(scoringAnswerRepository, rabbitTemplate);
    }

    @Test
    void dispatch_marksAnswerInFlightAndPublishesAllJobFields() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_REPEAT_SENTENCE);

        dispatcher.dispatch(answer);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        verify(scoringAnswerRepository).save(answer);
        ArgumentCaptor<AiScoringJob> jobCaptor = ArgumentCaptor.forClass(AiScoringJob.class);
        verify(rabbitTemplate).convertAndSend(
                eq(ScoringConstants.AI_SCORING_EXCHANGE),
                eq(ScoringConstants.AI_SCORING_ROUTING_KEY),
                jobCaptor.capture());
        AiScoringJob job = jobCaptor.getValue();
        assertThat(job.answerPublicId()).isEqualTo(answer.getAnswerPublicId());
        assertThat(job.attemptPublicId()).isEqualTo(answer.getAttemptPublicId());
        assertThat(job.sessionPublicId()).isEqualTo(answer.getSessionPublicId());
        assertThat(job.tenantId()).isEqualTo(answer.getTenantId());
        assertThat(job.taskType()).isEqualTo(answer.getTaskType());
        assertThat(job.payload()).isEqualTo(answer.getPayload());
        assertThat(job.referenceText()).isEqualTo(answer.getCorrectAnswerText());
    }

    @Test
    void dispatch_rejectsUnsupportedTypeBeforeChangingState() {
        ScoringAnswer answer = answer(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION);

        assertThatThrownBy(() -> dispatcher.dispatch(answer))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(ScoringConstants.TASK_TYPE_PERSONAL_INTRODUCTION);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.PENDING);
        verifyNoInteractions(scoringAnswerRepository, rabbitTemplate);
    }

    @Test
    void supports_isNullSafeForUnknownType() {
        assertThat(dispatcher.supports(null)).isFalse();
        assertThat(dispatcher.supports("UNKNOWN")).isFalse();
        assertThat(dispatcher.supports(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT)).isTrue();
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
