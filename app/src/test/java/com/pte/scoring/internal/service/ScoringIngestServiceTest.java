package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.itembank.TaskRuntimeProfileDescriptor;
import com.pte.itembank.TaskRuntimeProfileRegistry;
import com.pte.scoring.domain.AiEvaluation;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.internal.repository.AiEvaluationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringIngestServiceTest {

    @Mock
    private AttemptService attemptService;
    @Mock
    private AiEvaluationRepository evaluationRepository;

    private ScoringIngestService service;

    @BeforeEach
    void setUp() {
        service = new ScoringIngestService(attemptService, evaluationRepository);
    }

    @Test
    void ingestForSession_pullsSubmittedAnswersAndOpensAnEvaluationForNewOnes() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = view(sessionPublicId, tenantId, "READ_ALOUD");
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        when(evaluationRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.empty());
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingestForSession(sessionPublicId, tenantId);

        ArgumentCaptor<AiEvaluation> saved = ArgumentCaptor.forClass(AiEvaluation.class);
        verify(evaluationRepository).save(saved.capture());
        AiEvaluation evaluation = saved.getValue();
        assertThat(evaluation.getAnswerPublicId()).isEqualTo(view.answerPublicId());
        assertThat(evaluation.getAttemptPublicId()).isEqualTo(view.attemptPublicId());
        assertThat(evaluation.getItemPublicId()).isEqualTo(view.itemPublicId());
        assertThat(evaluation.getSessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(evaluation.getTenantId()).isEqualTo(tenantId);
        assertThat(evaluation.getExamDefinitionPublicId()).isEqualTo(view.examDefinitionPublicId());
        assertThat(evaluation.getTaskType()).isEqualTo("READ_ALOUD");
        assertThat(evaluation.getSection()).isEqualTo("SPEAKING");
    }

    @Test
    void ingestForSession_readsTheScoringMethodFromTheItemRuntime() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        List<SubmittedAnswerView> views = List.of(view(sessionPublicId, tenantId, "READ_ALOUD"),
                view(sessionPublicId, tenantId, "WRITE_ESSAY"), view(sessionPublicId, tenantId, "MC_READING_SINGLE"),
                view(sessionPublicId, tenantId, "PERSONAL_INTRODUCTION"),
                new SubmittedAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sessionPublicId,
                        tenantId, UUID.randomUUID(), "NO_RUNTIME", "payload", null, null, false, null, null, null,
                        null, null, "SPEAKING", null, null, null, null, null));
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(views);
        when(evaluationRepository.findByAnswerPublicId(any())).thenReturn(Optional.empty());
        ArgumentCaptor<AiEvaluation> saved = ArgumentCaptor.forClass(AiEvaluation.class);

        service.ingestForSession(sessionPublicId, tenantId);

        verify(evaluationRepository, times(5)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(AiEvaluation::getScoringMethod).containsExactly(
                ScoringMethod.AI_SPEECH, ScoringMethod.AI_TEXT, ScoringMethod.OBJECTIVE, ScoringMethod.UNSCORED,
                null);
    }

    @Test
    void ingestForSession_skipsAlreadyIngestedAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = view(sessionPublicId, tenantId, "READ_ALOUD");
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        AiEvaluation existing = new AiEvaluation(tenantId, sessionPublicId, view.attemptPublicId(),
                view.answerPublicId(), view.itemPublicId(), view.examDefinitionPublicId(), "READ_ALOUD", "SPEAKING",
                ScoringMethod.AI_SPEECH);
        when(evaluationRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.of(existing));

        service.ingestForSession(sessionPublicId, tenantId);

        verify(evaluationRepository, never()).save(any());
    }

    @Test
    void ingestForSession_swallowsConcurrentInsertException() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = view(sessionPublicId, tenantId, "READ_ALOUD");
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        when(evaluationRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.empty());
        when(evaluationRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // Should not throw
        service.ingestForSession(sessionPublicId, tenantId);

        verify(evaluationRepository).save(any(AiEvaluation.class));
    }

    @Test
    void ingestSubmittedAttempt_pullsOnlyThatAttemptsAnswers() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = view(UUID.randomUUID(), tenantId, "READ_ALOUD");
        when(attemptService.getSubmittedAnswersForAttempt(attemptPublicId, tenantId)).thenReturn(List.of(view));
        when(evaluationRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.empty());
        when(evaluationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingestSubmittedAttempt(attemptPublicId, tenantId);

        ArgumentCaptor<AiEvaluation> saved = ArgumentCaptor.forClass(AiEvaluation.class);
        verify(evaluationRepository).save(saved.capture());
        assertThat(saved.getValue().getAnswerPublicId()).isEqualTo(view.answerPublicId());
        verify(attemptService, never()).getSubmittedAnswersForSession(any(), any());
    }

    @Test
    void anEvaluationKeepsNoCopyOfTheSubmittedAnswerOrItsQuestion() {
        assertThat(Arrays.stream(AiEvaluation.class.getDeclaredFields()).map(Field::getName))
                .doesNotContain("payload", "correctAnswerText", "optionsJson", "promptText", "referenceAnswerText",
                        "imagePromptRef", "minWordCount", "maxWordCount", "expired");
    }

    private static SubmittedAnswerView view(UUID sessionPublicId, UUID tenantId, String taskType) {
        TaskRuntimeProfileDescriptor runtime = TaskRuntimeProfileRegistry.descriptorFor(taskType);
        return new SubmittedAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sessionPublicId,
                tenantId, UUID.randomUUID(), taskType, "payload", null, null, false, null, null, null, null, null,
                "SPEAKING", runtime, null, null, null, null);
    }
}
