package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.domain.enums.TimingMode;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-05: activation validation is deliberately NOT "weights sum to 100" (the
 * source V5 table itself doesn't, due to rounding) — only structural
 * soundness is enforced. No Spring context (plain JUnit + AssertJ), matching
 * this codebase's other stateless-validator tests.
 */
class ScoreTemplateActivationValidatorTest {

    /** The 22 scored PTE task types (mirrors itembank.PteTaskType's scored=true rows; this module stays independent of itembank). */
    private static final List<String> REQUIRED_TASK_TYPES = List.of(
            "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
            "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_BLANKS_READING",
            "FILL_BLANKS_READING_WRITING",
            "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_BLANKS_LISTENING",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");

    /** One valid item per required task type: min=1/max=2, every weight column gets >0 from at least one item. */
    private ScoreTemplate validTemplate() {
        ScoreTemplate template = new ScoreTemplate();
        template.setCode("TEST");
        template.setVersion(1);
        template.setName("Test template");
        int seq = 0;
        for (String taskType : REQUIRED_TASK_TYPES) {
            template.addItem(item(taskType, seq++, 1, 2, BigDecimal.ONE));
        }
        return template;
    }

    private ScoreTemplateItem item(String taskType, int sequence, int min, int max, BigDecimal weight) {
        ScoreTemplateItem item = new ScoreTemplateItem();
        item.setTaskType(taskType);
        item.setSection("SPEAKING");
        item.setSequence(sequence);
        item.setMinCount(min);
        item.setMaxCount(max);
        item.setPrepSeconds(0);
        item.setResponseSeconds(30);
        item.setTimingMode(TimingMode.FIXED);
        item.setScoringMethod(ScoringMethod.AI_SPEECH);
        item.setOverallWeight(weight);
        item.setSpeakingWeight(weight);
        item.setWritingWeight(weight);
        item.setReadingWeight(weight);
        item.setListeningWeight(weight);
        return item;
    }

    @Test
    void validate_wellFormedTemplate_doesNotThrow() {
        assertThatCode(() -> ScoreTemplateActivationValidator.validate(validTemplate())).doesNotThrowAnyException();
    }

    @Test
    void validate_missingOneRequiredTaskType_throws() {
        ScoreTemplate template = validTemplate();
        template.getItems().remove(0); // drops READ_ALOUD

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("READ_ALOUD");
    }

    @Test
    void validate_minCountGreaterThanMaxCount_throws() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(0).setMinCount(5);
        template.getItems().get(0).setMaxCount(3);

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }

    @Test
    void validate_maxCountZero_throws() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(0).setMinCount(0);
        template.getItems().get(0).setMaxCount(0);

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }

    @Test
    void validate_negativeWeight_throws() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(0).setSpeakingWeight(BigDecimal.valueOf(-1));

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }

    @Test
    void validate_oneSkillTotalWeightZero_throws() {
        ScoreTemplate template = validTemplate();
        for (ScoreTemplateItem item : template.getItems()) {
            item.setListeningWeight(BigDecimal.ZERO);
        }

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("LISTENING");
    }

    @Test
    void validate_noItems_throws() {
        ScoreTemplate template = new ScoreTemplate();
        template.setItems(new ArrayList<>());

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }
}
