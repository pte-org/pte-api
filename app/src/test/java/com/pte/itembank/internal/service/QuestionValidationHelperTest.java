package com.pte.itembank.internal.service;

import com.pte.itembank.domain.Question;
import com.pte.itembank.domain.QuestionOption;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.itembank.internal.exception.QuestionValidationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionValidationHelperTest {

    private final QuestionValidationHelper helper = new QuestionValidationHelper(null);

    @Test
    void reorderParagraphs_usesOrderIndexAsCorrectPosition_notCorrectFlag() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.RE_ORDER_PARAGRAPHS);
        for (int index = 0; index < 4; index++) {
            QuestionOption option = new QuestionOption();
            option.setText("Paragraph " + index);
            option.setOrderIndex(index);
            option.setCorrect(false);
            question.addOption(option);
        }

        assertThatCode(() -> helper.validate(question)).doesNotThrowAnyException();
    }

    @Test
    void readAloud_missingPromptText_throws() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.READ_ALOUD);

        assertThatThrownBy(() -> helper.validate(question)).isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void mcReadingSingle_noCorrectOption_throws() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.MC_READING_SINGLE);
        question.setPromptText("Passage text");
        QuestionOption option = new QuestionOption();
        option.setText("A");
        option.setOrderIndex(0);
        option.setCorrect(false);
        question.addOption(option);

        assertThatThrownBy(() -> helper.validate(question)).isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void describeImage_missingImagePromptRef_throws() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.DESCRIBE_IMAGE);

        assertThatThrownBy(() -> helper.validate(question)).isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void writeEssay_missingWordCount_throws() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.WRITE_ESSAY);
        question.setPromptText("Essay prompt");
        question.setCorrectAnswerText("N/A");

        assertThatThrownBy(() -> helper.validate(question)).isInstanceOf(QuestionValidationException.class);
    }

    @Test
    void repeatSentence_missingReferenceText_isRejectedWhileAuthoring() {
        Question question = repeatSentence(null);

        assertThatThrownBy(() -> helper.validate(question))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.REFERENCE_ANSWER_REQUIRED);
    }

    @Test
    void repeatSentence_blankReferenceText_isRejectedWhileAuthoring() {
        Question question = repeatSentence("   ");

        assertThatThrownBy(() -> helper.validate(question))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.REFERENCE_ANSWER_REQUIRED);
    }

    @Test
    void repeatSentence_withReferenceText_isAccepted() {
        assertThatCode(() -> helper.validate(repeatSentence("The library opens at nine."))).doesNotThrowAnyException();
    }

    @Test
    void summarizeSpokenText_missingTranscript_isRejectedWhileAuthoring() {
        assertThatThrownBy(() -> helper.validate(summarizeSpokenText(null)))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.PROMPT_TEXT_REQUIRED);
    }

    @Test
    void summarizeSpokenText_blankTranscript_isRejectedWhileAuthoring() {
        assertThatThrownBy(() -> helper.validate(summarizeSpokenText("  ")))
                .isInstanceOf(QuestionValidationException.class)
                .hasMessage(ItembankConstants.PROMPT_TEXT_REQUIRED);
    }

    @Test
    void summarizeSpokenText_withTranscript_isAccepted() {
        assertThatCode(() -> helper.validate(summarizeSpokenText("A lecture about bees."))).doesNotThrowAnyException();
    }

    private static Question summarizeSpokenText(String promptText) {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.SUMMARIZE_SPOKEN_TEXT);
        question.setAudioPromptRef(UUID.randomUUID());
        question.setMinWordCount(50);
        question.setMaxWordCount(70);
        question.setPromptText(promptText);
        return question;
    }

    private static Question repeatSentence(String referenceAnswerText) {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.REPEAT_SENTENCE);
        question.setAudioPromptRef(UUID.randomUUID());
        question.setReferenceAnswerText(referenceAnswerText);
        return question;
    }
}
