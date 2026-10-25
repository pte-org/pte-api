package com.pte.attempt.internal.service.query;

import com.pte.attempt.internal.service.ExamContentService;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmittedAnswerQueryServiceTest {

    private static final UUID EXAM_DEFINITION_ID = UUID.randomUUID();
    private static final UUID EXAM_VERSION_ID = UUID.randomUUID();

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    @Mock
    private ExamContentService contentService;

    private SubmittedAnswerQueryService service;
    private final UUID sessionPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new SubmittedAnswerQueryService(attemptAnswerRepository, contentService);
    }

    private ExamAttempt attempt() {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setTenantId(tenantId);
        attempt.setExamVersionPublicId(EXAM_VERSION_ID);
        attempt.setExamDefinitionPublicId(EXAM_DEFINITION_ID);
        return attempt;
    }

    private static SnapshotContentResponse.Item item(UUID itemId, String taskType, String promptText,
            String referenceAnswerText, UUID imagePromptRef, Integer minWordCount, Integer maxWordCount,
            String correctAnswerText, String optionsJson) {
        return new SnapshotContentResponse.Item(0, "READING", taskType, "title", promptText, null, imagePromptRef,
                referenceAnswerText, correctAnswerText, minWordCount, maxWordCount, optionsJson, taskType, "title",
                taskType, null, null, null, null, itemId);
    }

    private void exam(SnapshotContentResponse.Item... items) {
        Map<UUID, SnapshotContentResponse.Item> byId = new LinkedHashMap<>();
        for (SnapshotContentResponse.Item item : items) {
            byId.put(item.itemPublicId(), item);
        }
        when(contentService.contentItems(EXAM_VERSION_ID)).thenReturn(byId);
    }

    private AttemptAnswer answer(ExamAttempt attempt, UUID itemId, String payload, boolean expired) {
        AttemptAnswer answer = new AttemptAnswer();
        answer.setPublicId(UUID.randomUUID());
        answer.setAttempt(attempt);
        answer.setItemPublicId(itemId);
        answer.setPayload(payload);
        answer.setExpired(expired);
        return answer;
    }

    @Test
    void findForSession_mapsAttemptAnswerToSubmittedAnswerViewCorrectly() {
        UUID itemId = UUID.randomUUID();
        ExamAttempt attempt = attempt();
        exam(item(itemId, "MC_READING_SINGLE", null, null, null, null, null,
                "[{\"text\":\"A\",\"correct\":true}]", "[{\"text\":\"A\",\"orderIndex\":0}]"));
        AttemptAnswer answer = answer(attempt, itemId, "0", false);
        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(1);
        SubmittedAnswerView view = results.get(0);
        assertThat(view.answerPublicId()).isEqualTo(answer.getPublicId());
        assertThat(view.attemptPublicId()).isEqualTo(attempt.getPublicId());
        assertThat(view.itemPublicId()).isEqualTo(itemId);
        assertThat(view.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(view.tenantId()).isEqualTo(tenantId);
        assertThat(view.taskType()).isEqualTo("MC_READING_SINGLE");
        assertThat(view.payload()).isEqualTo("0");
        assertThat(view.correctAnswerText()).isEqualTo("[{\"text\":\"A\",\"correct\":true}]");
        assertThat(view.optionsJson()).isEqualTo("[{\"text\":\"A\",\"orderIndex\":0}]");
        assertThat(view.expired()).isFalse();
        assertThat(view.examDefinitionPublicId()).isEqualTo(EXAM_DEFINITION_ID);
    }

    @Test
    void findForSession_carriesQuestionContentForAiScoring() {
        UUID itemId = UUID.randomUUID();
        UUID imagePromptRef = UUID.randomUUID();
        ExamAttempt attempt = attempt();
        exam(item(itemId, "DESCRIBE_IMAGE", "Describe the chart.", "Sales rose steadily.", imagePromptRef, 5, 75,
                null, null));
        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer(attempt, itemId, "media-ref", false)));

        SubmittedAnswerView view = service.findForSession(sessionPublicId, tenantId).get(0);

        assertThat(view.promptText()).isEqualTo("Describe the chart.");
        assertThat(view.referenceAnswerText()).isEqualTo("Sales rose steadily.");
        assertThat(view.imagePromptRef()).isEqualTo(imagePromptRef);
        assertThat(view.minWordCount()).isEqualTo(5);
        assertThat(view.maxWordCount()).isEqualTo(75);
    }

    @Test
    void findForSession_mapsExpiredFlag() {
        UUID itemId = UUID.randomUUID();
        ExamAttempt attempt = attempt();
        exam(item(itemId, "MC_READING_SINGLE", null, null, null, null, null, "a", "o"));
        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer(attempt, itemId, "", true)));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).expired()).isTrue();
    }

    @Test
    void findForSession_returnsEmptyListWhenNoAnswersFound() {
        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of());

        assertThat(service.findForSession(sessionPublicId, tenantId)).isEmpty();
    }

    @Test
    void findForSession_mapsMultipleAnswers() {
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        ExamAttempt attempt = attempt();
        exam(item(firstItem, "MC_READING_SINGLE", null, null, null, null, null, "answer1", "options1"),
                item(secondItem, "MC_READING_MULTIPLE", null, null, null, null, null, "answer2", "options2"));
        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer(attempt, firstItem, "payload1", false),
                        answer(attempt, secondItem, "payload2", true)));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).taskType()).isEqualTo("MC_READING_SINGLE");
        assertThat(results.get(0).expired()).isFalse();
        assertThat(results.get(1).taskType()).isEqualTo("MC_READING_MULTIPLE");
        assertThat(results.get(1).expired()).isTrue();
    }

    @Test
    void findForAttempt_answerWhoseItemIsNotInTheExam_failsLoudly() {
        ExamAttempt attempt = attempt();
        exam();
        when(attemptAnswerRepository.findByAttempt_PublicIdAndAttempt_TenantId(attempt.getPublicId(), tenantId))
                .thenReturn(List.of(answer(attempt, UUID.randomUUID(), "x", false)));

        assertThatThrownBy(() -> service.findForAttempt(attempt.getPublicId(), tenantId))
                .isInstanceOf(IllegalStateException.class);
    }
}
