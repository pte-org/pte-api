package com.pte.scoring.internal.service;

import com.pte.attempt.AttemptService;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.scoring.domain.ScoringAnswer;
import com.pte.scoring.internal.repository.ScoringAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
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
                sessionPublicId, tenantId, UUID.randomUUID(), "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false, null, null, null, null, null);

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
                sessionPublicId, tenantId, UUID.randomUUID(), "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false, null, null, null, null, null);

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
                sessionPublicId, tenantId, UUID.randomUUID(), "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false, null, null, null, null, null);

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
    void ingestSubmittedAttempt_pullsOnlyThatAttemptsAnswers() {
        UUID attemptPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, UUID.randomUUID(),
                UUID.randomUUID(), tenantId, UUID.randomUUID(), "MC_READING_SINGLE", "1",
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]", false, null, null, null, null, null);
        when(attemptService.getSubmittedAnswersForAttempt(attemptPublicId, tenantId)).thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(answerPublicId)).thenReturn(Optional.empty());
        when(scoringAnswerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.ingestSubmittedAttempt(attemptPublicId, tenantId);

        ArgumentCaptor<ScoringAnswer> saved = ArgumentCaptor.forClass(ScoringAnswer.class);
        verify(scoringAnswerRepository).save(saved.capture());
        assertThat(saved.getValue().getAnswerPublicId()).isEqualTo(answerPublicId);
        verify(attemptService, never()).getSubmittedAnswersForSession(any(), any());
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
        UUID scoreTemplatePublicId = UUID.randomUUID();

        SubmittedAnswerView view = new SubmittedAnswerView(
                answerPublicId, attemptPublicId, pinnedItemPublicId,
                sessionPublicId, tenantId, scoreTemplatePublicId, taskType, payload,
                correctAnswerText, optionsJson, true, null, null, null, null, null);

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
            capturedAnswer.setScoreTemplatePublicId(arg.getScoreTemplatePublicId());
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
        assertThat(capturedAnswer.getScoreTemplatePublicId()).isEqualTo(scoreTemplatePublicId);
        assertThat(capturedAnswer.getTaskType()).isEqualTo(taskType);
        assertThat(capturedAnswer.getPayload()).isEqualTo(payload);
        assertThat(capturedAnswer.getCorrectAnswerText()).isEqualTo(correctAnswerText);
        assertThat(capturedAnswer.getOptionsJson()).isEqualTo(optionsJson);
        assertThat(capturedAnswer.isExpired()).isTrue();
    }

    @Test
    void ingestForSession_copiesQuestionContentForEachAiTaskType() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID imageRef = UUID.randomUUID();
        List<SubmittedAnswerView> views = List.of(
                questionView(sessionPublicId, tenantId, "READ_ALOUD", "Passage to read.", null, null, null, null),
                questionView(sessionPublicId, tenantId, "RETELL_LECTURE", null, "Lecture transcript.", null, null, null),
                questionView(sessionPublicId, tenantId, "ANSWER_SHORT_QUESTION", "What is frozen water?", "ice",
                        null, null, null),
                questionView(sessionPublicId, tenantId, "DESCRIBE_IMAGE", null, null, imageRef, null, null),
                questionView(sessionPublicId, tenantId, "SUMMARIZE_WRITTEN_TEXT", "Source passage.", null, null, 5, 75));
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(views);
        when(scoringAnswerRepository.findByAnswerPublicId(any())).thenReturn(Optional.empty());
        ArgumentCaptor<ScoringAnswer> saved = ArgumentCaptor.forClass(ScoringAnswer.class);

        service.ingestForSession(sessionPublicId, tenantId);

        verify(scoringAnswerRepository, times(5)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ScoringAnswer::getTaskType, ScoringAnswer::getPromptText,
                        ScoringAnswer::getReferenceAnswerText, ScoringAnswer::getImagePromptRef,
                        ScoringAnswer::getMinWordCount, ScoringAnswer::getMaxWordCount)
                .containsExactly(
                        tuple("READ_ALOUD", "Passage to read.", null, null, null, null),
                        tuple("RETELL_LECTURE", null, "Lecture transcript.", null, null, null),
                        tuple("ANSWER_SHORT_QUESTION", "What is frozen water?", "ice", null, null, null),
                        tuple("DESCRIBE_IMAGE", null, null, imageRef, null, null),
                        tuple("SUMMARIZE_WRITTEN_TEXT", "Source passage.", null, null, 5, 75));
    }

    @Test
    void ingestForSession_neverRewritesAnAlreadyIngestedRow() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = questionView(sessionPublicId, tenantId, "SUMMARIZE_WRITTEN_TEXT",
                "Source passage.", null, null, 5, 75);
        ScoringAnswer legacyRow = new ScoringAnswer();
        legacyRow.setAnswerPublicId(view.answerPublicId());
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.of(legacyRow));

        service.ingestForSession(sessionPublicId, tenantId);

        verify(scoringAnswerRepository, never()).save(any());
        assertThat(legacyRow.getPromptText()).isNull();
    }

    @Test
    void ingestForScoring_backfillsQuestionContentOnRowIngestedWithoutIt() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        SubmittedAnswerView view = questionView(sessionPublicId, tenantId, "SUMMARIZE_WRITTEN_TEXT",
                "Source passage.", null, null, 5, 75);
        ScoringAnswer legacyRow = new ScoringAnswer();
        legacyRow.setAnswerPublicId(view.answerPublicId());
        legacyRow.setPayload("old payload stays");
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.of(legacyRow));

        service.ingestForScoring(sessionPublicId, tenantId);

        verify(scoringAnswerRepository).save(legacyRow);
        assertThat(legacyRow.getPromptText()).isEqualTo("Source passage.");
        assertThat(legacyRow.getMinWordCount()).isEqualTo(5);
        assertThat(legacyRow.getMaxWordCount()).isEqualTo(75);
        assertThat(legacyRow.getPayload()).isEqualTo("old payload stays");
    }

    @Test
    void ingestForScoring_doesNotRewriteRowWhoseQuestionContentIsCurrent() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID imageRef = UUID.randomUUID();
        SubmittedAnswerView view = questionView(sessionPublicId, tenantId, "DESCRIBE_IMAGE", null, null, imageRef,
                null, null);
        ScoringAnswer currentRow = new ScoringAnswer();
        currentRow.setAnswerPublicId(view.answerPublicId());
        currentRow.applyQuestionContent(null, null, imageRef, null, null);
        when(attemptService.getSubmittedAnswersForSession(sessionPublicId, tenantId)).thenReturn(List.of(view));
        when(scoringAnswerRepository.findByAnswerPublicId(view.answerPublicId())).thenReturn(Optional.of(currentRow));

        service.ingestForScoring(sessionPublicId, tenantId);

        verify(scoringAnswerRepository, never()).save(any());
    }

    private static SubmittedAnswerView questionView(UUID sessionPublicId, UUID tenantId, String taskType,
            String promptText, String referenceAnswerText, UUID imagePromptRef, Integer minWordCount,
            Integer maxWordCount) {
        return new SubmittedAnswerView(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), sessionPublicId,
                tenantId, UUID.randomUUID(), taskType, "payload", null, null, false,
                promptText, referenceAnswerText, imagePromptRef, minWordCount, maxWordCount);
    }
}
