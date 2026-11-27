package com.pte.itembank.internal.service.lifecycle;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.audit.AuditLogChangeWriter;
import com.pte.shared.audit.AuditStateChange;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/** A draft edit keeps the answer key before and after, so a reviewer sees who changed which option. */
@ExtendWith(MockitoExtension.class)
class QuestionUpdateAuditTest {

    private static final CurrentUser STAFF = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_STAFF"));

    @Mock
    private AuditLogChangeWriter changeWriter;

    @Test
    void recordsTheAnswerKeyChangeWithOptionsInStableOrder() {
        Question question = question();
        QuestionAuditState before = QuestionAuditState.of(question);
        question.getOptions().forEach(option -> option.setCorrect(!option.isCorrect()));

        new QuestionUpdateAudit(changeWriter).record(STAFF, question, before, "Updated question");

        ArgumentCaptor<AuditStateChange> captor = ArgumentCaptor.forClass(AuditStateChange.class);
        verify(changeWriter).recordState(eq(STAFF), captor.capture());
        AuditStateChange change = captor.getValue();
        assertThat(change.aggregateType()).isEqualTo(ItembankConstants.QUESTION_AGGREGATE);
        assertThat(change.action()).isEqualTo(ItembankConstants.AUDIT_UPDATED);
        QuestionAuditState recordedBefore = (QuestionAuditState) change.before();
        QuestionAuditState recordedAfter = (QuestionAuditState) change.after();
        assertThat(recordedBefore.options()).extracting(QuestionAuditState.OptionState::orderIndex)
                .containsExactly(0, 1);
        assertThat(recordedBefore.options()).extracting(QuestionAuditState.OptionState::correct)
                .containsExactly(true, false);
        assertThat(recordedAfter.options()).extracting(QuestionAuditState.OptionState::correct)
                .containsExactly(false, true);
        assertThat(recordedAfter.audioPromptRef()).isEqualTo(question.getAudioPromptRef());
    }

    @Test
    void longSummaryIsCutToTheColumnLimit() {
        Question question = question();

        new QuestionUpdateAudit(changeWriter).record(STAFF, question, QuestionAuditState.of(question),
                "x".repeat(ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH + 50));

        ArgumentCaptor<AuditStateChange> captor = ArgumentCaptor.forClass(AuditStateChange.class);
        verify(changeWriter).recordState(eq(STAFF), captor.capture());
        assertThat(captor.getValue().summary()).hasSize(ItembankConstants.AUDIT_SUMMARY_MAX_LENGTH);
    }

    private static Question question() {
        Question question = new Question();
        question.setPublicId(UUID.randomUUID());
        question.setTaskTypeKey("MCQ_SINGLE");
        question.setPromptText("Which is correct?");
        question.setAudioPromptRef(UUID.randomUUID());
        question.addOption(option(1, "B", false));
        question.addOption(option(0, "A", true));
        return question;
    }

    private static QuestionOption option(int orderIndex, String text, boolean correct) {
        QuestionOption option = new QuestionOption();
        option.setOrderIndex(orderIndex);
        option.setText(text);
        option.setCorrect(correct);
        return option;
    }
}
