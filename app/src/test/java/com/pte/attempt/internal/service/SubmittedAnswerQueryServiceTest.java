package com.pte.attempt.internal.service;

import com.pte.attempt.domain.AttemptAnswer;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.PinnedItem;
import com.pte.attempt.dto.response.SubmittedAnswerView;
import com.pte.attempt.internal.repository.AttemptAnswerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubmittedAnswerQueryServiceTest {

    @Mock
    private AttemptAnswerRepository attemptAnswerRepository;

    private SubmittedAnswerQueryService service;

    @BeforeEach
    void setUp() {
        service = new SubmittedAnswerQueryService(attemptAnswerRepository);
    }

    @Test
    void findForSession_mapsAttemptAnswerToSubmittedAnswerViewCorrectly() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID answerPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID pinnedItemPublicId = UUID.randomUUID();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(attemptPublicId);
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setTenantId(tenantId);

        PinnedItem pinnedItem = new PinnedItem();
        pinnedItem.setPublicId(pinnedItemPublicId);
        pinnedItem.setTaskType("MC_READING_SINGLE");
        pinnedItem.setCorrectAnswerText("[{\"text\":\"A\",\"correct\":true}]");
        pinnedItem.setOptionsJson("[{\"text\":\"A\",\"orderIndex\":0}]");

        AttemptAnswer answer = new AttemptAnswer();
        answer.setPublicId(answerPublicId);
        answer.setAttempt(attempt);
        answer.setPinnedItem(pinnedItem);
        answer.setPayload("0");
        answer.setExpired(false);

        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(1);
        SubmittedAnswerView view = results.get(0);
        assertThat(view.answerPublicId()).isEqualTo(answerPublicId);
        assertThat(view.attemptPublicId()).isEqualTo(attemptPublicId);
        assertThat(view.pinnedItemPublicId()).isEqualTo(pinnedItemPublicId);
        assertThat(view.sessionPublicId()).isEqualTo(sessionPublicId);
        assertThat(view.tenantId()).isEqualTo(tenantId);
        assertThat(view.taskType()).isEqualTo("MC_READING_SINGLE");
        assertThat(view.payload()).isEqualTo("0");
        assertThat(view.correctAnswerText()).isEqualTo("[{\"text\":\"A\",\"correct\":true}]");
        assertThat(view.optionsJson()).isEqualTo("[{\"text\":\"A\",\"orderIndex\":0}]");
        assertThat(view.expired()).isFalse();
    }

    @Test
    void findForSession_mapsExpiredFlag() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setTenantId(tenantId);

        PinnedItem pinnedItem = new PinnedItem();
        pinnedItem.setPublicId(UUID.randomUUID());
        pinnedItem.setTaskType("MC_READING_SINGLE");
        pinnedItem.setCorrectAnswerText("[{\"text\":\"A\",\"correct\":true}]");
        pinnedItem.setOptionsJson("[{\"text\":\"A\",\"orderIndex\":0}]");

        AttemptAnswer answer = new AttemptAnswer();
        answer.setPublicId(UUID.randomUUID());
        answer.setAttempt(attempt);
        answer.setPinnedItem(pinnedItem);
        answer.setPayload("");
        answer.setExpired(true);

        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).expired()).isTrue();
    }

    @Test
    void findForSession_returnsEmptyListWhenNoAnswersFound() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of());

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).isEmpty();
    }

    @Test
    void findForSession_mapsMultipleAnswers() {
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        ExamAttempt attempt = new ExamAttempt();
        attempt.setPublicId(UUID.randomUUID());
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setTenantId(tenantId);

        PinnedItem pinnedItem1 = new PinnedItem();
        pinnedItem1.setPublicId(UUID.randomUUID());
        pinnedItem1.setTaskType("MC_READING_SINGLE");
        pinnedItem1.setCorrectAnswerText("answer1");
        pinnedItem1.setOptionsJson("options1");

        PinnedItem pinnedItem2 = new PinnedItem();
        pinnedItem2.setPublicId(UUID.randomUUID());
        pinnedItem2.setTaskType("MC_READING_MULTIPLE");
        pinnedItem2.setCorrectAnswerText("answer2");
        pinnedItem2.setOptionsJson("options2");

        AttemptAnswer answer1 = new AttemptAnswer();
        answer1.setPublicId(UUID.randomUUID());
        answer1.setAttempt(attempt);
        answer1.setPinnedItem(pinnedItem1);
        answer1.setPayload("payload1");
        answer1.setExpired(false);

        AttemptAnswer answer2 = new AttemptAnswer();
        answer2.setPublicId(UUID.randomUUID());
        answer2.setAttempt(attempt);
        answer2.setPinnedItem(pinnedItem2);
        answer2.setPayload("payload2");
        answer2.setExpired(true);

        when(attemptAnswerRepository.findByAttempt_SessionPublicIdAndAttempt_TenantId(sessionPublicId, tenantId))
                .thenReturn(List.of(answer1, answer2));

        List<SubmittedAnswerView> results = service.findForSession(sessionPublicId, tenantId);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).taskType()).isEqualTo("MC_READING_SINGLE");
        assertThat(results.get(0).expired()).isFalse();
        assertThat(results.get(1).taskType()).isEqualTo("MC_READING_MULTIPLE");
        assertThat(results.get(1).expired()).isTrue();
    }
}
