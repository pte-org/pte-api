package com.pte.authoring.service;

import com.pte.authoring.domain.Question;
import com.pte.authoring.domain.QuestionOption;
import com.pte.authoring.domain.enums.PteTaskType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

class QuestionValidationHelperTest {

    private final QuestionValidationHelper helper = new QuestionValidationHelper();

    @Test
    void reorderParagraphs_usesOrderIndexAsCorrectPosition_notCorrectFlag() {
        Question question = new Question();
        question.setPteTaskType(PteTaskType.RE_ORDER_PARAGRAPHS);
        question.setTitle("Re-order paragraphs fixture");
        for (int index = 0; index < 4; index++) {
            QuestionOption option = new QuestionOption();
            option.setText("Paragraph " + index);
            option.setOrderIndex(index);
            option.setCorrect(false);
            question.addOption(option);
        }

        assertThatCode(() -> helper.validate(question)).doesNotThrowAnyException();
    }
}
