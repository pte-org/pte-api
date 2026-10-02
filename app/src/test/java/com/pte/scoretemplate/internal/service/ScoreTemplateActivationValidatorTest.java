package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.domain.enums.ScoringMethod;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FR-05: structural soundness, plus an exact-100 total on each of the 4
 * skill weight columns (OVERALL is exempt — never required to hit a fixed
 * total). No Spring context (plain JUnit + AssertJ), matching this
 * codebase's other stateless-validator tests.
 */
class ScoreTemplateActivationValidatorTest {

    /** The 22 scored PTE task types (mirrors itembank.PteTaskType's scored=true rows; this module stays independent of itembank). */
    private static final List<String> REQUIRED_TASK_TYPES = List.of(
            "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
            "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
            "FILL_IN_THE_BLANKS_DROPDOWN",
            "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");

    /**
     * One valid item per required task type: min=1/max=2. Every weight goes
     * to the last item (100) and every other item gets 0 — the simplest
     * distribution that satisfies both "every skill total > 0" and the new
     * "every skill total == exactly 100" check.
     */
    private ScoreTemplate validTemplate() {
        ScoreTemplate template = new ScoreTemplate();
        template.setCode("TEST");
        template.setVersion(1);
        template.setName("Test template");
        int lastIndex = REQUIRED_TASK_TYPES.size() - 1;
        for (int i = 0; i <= lastIndex; i++) {
            BigDecimal weight = i == lastIndex ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
            template.addItem(item(REQUIRED_TASK_TYPES.get(i), i, 1, 2, weight));
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
    void validate_duplicateTaskType_reportsDuplicateInsteadOfSilentlyAcceptingIt() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(1).setTaskType(template.getItems().get(0).getTaskType());

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("Duplicate task types")
                .hasMessageContaining("READ_ALOUD");
    }

    @Test
    void validate_duplicateSequence_reportsDuplicateInsteadOfSilentlyAcceptingIt() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(1).setSequence(template.getItems().get(0).getSequence());

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("Duplicate template sequence values")
                .hasMessageContaining("0");
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
    void validate_skillWeightTotalUnder100_throws() {
        ScoreTemplate template = validTemplate();
        // Still >0 (passes the positive-weight check) but short of 100.
        template.getItems().get(template.getItems().size() - 1).setReadingWeight(BigDecimal.valueOf(99));

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("READING");
    }

    @Test
    void validate_skillWeightTotalOver100_throws() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(0).setWritingWeight(BigDecimal.ONE);

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class)
                .hasMessageContaining("WRITING");
    }

    @Test
    void validate_overallWeightNotExactly100_doesNotThrow() {
        ScoreTemplate template = validTemplate();
        template.getItems().get(template.getItems().size() - 1).setOverallWeight(BigDecimal.valueOf(50));
        template.getItems().get(0).setOverallWeight(BigDecimal.valueOf(3));

        assertThatCode(() -> ScoreTemplateActivationValidator.validate(template)).doesNotThrowAnyException();
    }

    @Test
    void validate_noItems_throws() {
        ScoreTemplate template = new ScoreTemplate();
        template.setItems(new ArrayList<>());

        assertThatThrownBy(() -> ScoreTemplateActivationValidator.validate(template))
                .isInstanceOf(ScoreTemplateValidationException.class);
    }
}
