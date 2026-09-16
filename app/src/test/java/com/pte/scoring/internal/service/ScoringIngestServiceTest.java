package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScoringIngestServiceTest {

    @Mock
    private AttemptService attemptService;
    @Mock
    private ScoringAnswerRepository scoringAnswerRepository;

    private ScoringIngestService service;

    @BeforeEach
    void setUp() {
        service = new ScoringIngestService(attemptService, scoringAnswerRepository);
    }

    @Test
    void ingestForSession_pullsSubmittedAnswersAndIngestsNewOnes() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();

        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, pinnedItemPublicId,
                sessionPublicId, tenantId, "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false);

        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.empty());
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingestForSession(sessionPublicId, tenantId);

        verify(scoringAnswerRepository).save(any(ScoringAnswer.class));
    }

    @Test
    void ingestForSession_skipsAlreadyIngestedAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();

        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, pinnedItemPublicId,
                sessionPublicId, tenantId, "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false);

        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(view));
        ScoringAnswer existingAnswer = new ScoringAnswer();
        existingAnswer.setAnswerPublicId(answerPublicId);
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.of(existingAnswer));

        service.ingestForSession(sessionPublicId, tenantId);

        verify(scoringAnswerRepository, never()).save(any());
    }

    @Test
    void ingestForSession_swallowsConcurrentInsertException() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();

        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, pinnedItemPublicId,
                sessionPublicId, tenantId, "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false);

        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.empty());
        when(scoringAnswerRepository.save(any()))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation"));

        // Should not throw
        service.ingestForSession(sessionPublicId, tenantId);

        verify(scoringAnswerRepository).save(any(ScoringAnswer.class));
    }

    @Test
    void ingestForSession_copiesAllFieldsCorrectly() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();
        String correctAnswerText = "[{\"text\":\"Correct\",\"correct\":true}]";
        String optionsJson = "[{\"text\":\"A\",\"orderIndex\":0}]";
        String payload = "0";
        String taskType = "MC_READING_SINGLE";

        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, pinnedItemPublicId,
                sessionPublicId, tenantId, taskType, payload,
                correctAnswerText, optionsJson, true);

        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId))
                .thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId))
                .thenReturn(Optional.empty());

        ScoringAnswer capturedAnswer = new ScoringAnswer();
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> {
            ScoringAnswer arg = inv.getArgument(0);
            capturedAnswer.setAnswerPublicId(arg.getAnswerPublicId());
            capturedAnswer.setAttemptPublicId(arg.getAttemptPublicId());
            capturedAnswer.setPinnedItemPublicId(arg.getPinnedItemPublicId());
            capturedAnswer.setSessionPublicId(arg.getSessionPublicId());
            capturedAnswer.setTenantId(arg.getTenantId());
            capturedAnswer.setTaskType(arg.getTaskType());
            capturedAnswer.setPayload(arg.getPayload());
            capturedAnswer.setCorrectAnswerText(arg.getCorrectAnswerText());
            capturedAnswer.setOptionsJson(arg.getOptionsJson());
            capturedAnswer.setExpired(arg.isExpired());
            return arg;
        });

        service.ingestForSession(sessionPublicId, tenantId);

        assertThat(capturedAnswer.getAnswerPublicId()).isEqualTo(answerPublicId);
        assertThat(capturedAnswer.getAttemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(capturedAnswer.getPinnedItemPublicId()).isEqualTo(pinnedItemPublicId);
        assertThat(capturedAnswer.getSessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(capturedAnswer.getTenantId()).isEqualTo(tenantId);
        assertThat(capturedAnswer.getTaskType()).isEqualTo(taskType);
        assertThat(capturedAnswer.getPayload()).isEqualTo(payload);
        assertThat(capturedAnswer.getCorrectAnswerText()).isEqualTo(correctAnswerText);
        assertThat(capturedAnswer.getOptionsJson()).isEqualTo(optionsJson);
        assertThat(capturedAnswer.isExpired()).isTrue();
    }
}
