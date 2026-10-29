package com.pte.assessment.internal.config;

import com.pte.assessment.internal.exception.content.TaskTimingNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies the shared fallback timing resource used by preview and attempts. */
class TaskTimingConfigTest {

    private final TaskTimingConfig config = new TaskTimingConfig(JsonMapper.builder().build());

    @Test
    void personalIntroductionIsConfiguredWithStaticPrepAndResponseSeconds() {
        TaskTimingConfig.Timing timing = config.timingFor("PERSONAL_INTRODUCTION");

        assertThat(timing.prepSeconds()).isEqualTo(25);
        assertThat(timing.responseSeconds()).isEqualTo(30);
        assertThat(timing.preListenSeconds()).isNull();
        assertThat(timing.preRecordSeconds()).isNull();
    }

    @Test
    void anUnconfiguredTaskTypeStillFailsFastRatherThanSilentlyDefaulting() {
        assertThatThrownBy(() -> config.timingFor("SOME_FUTURE_TASK_TYPE"))
                .isInstanceOf(TaskTimingNotConfiguredException.class);
    }

    @ParameterizedTest(name = "{0} still carries only preListenSeconds")
    @ValueSource(strings = {"REPEAT_SENTENCE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
            "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION"})
    void audioPromptTypesKeepOnlyPreListenSeconds(String taskType) {
        TaskTimingConfig.Timing timing = config.timingFor(taskType);

        assertThat(timing.preListenSeconds()).isNotNull();
        assertThat(timing.preRecordSeconds()).isNull();
        assertThat(timing.prepSeconds()).isZero();
        assertThat(timing.responseSeconds()).isZero();
    }

    @Test
    void repeatSentenceAndAnswerShortQuestionKeepTheirNoPreparationBeepValue() {
        assertThat(config.timingFor("REPEAT_SENTENCE").preListenSeconds()).isEqualTo(3);
        assertThat(config.timingFor("ANSWER_SHORT_QUESTION").preListenSeconds()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0} moved entirely to ScoreTemplate")
    @ValueSource(strings = {
            "READ_ALOUD", "DESCRIBE_IMAGE", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS",
            "FILL_IN_THE_BLANKS_DRAG_AND_DROP", "FILL_IN_THE_BLANKS_DROPDOWN", "SUMMARIZE_SPOKEN_TEXT",
            "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS",
            "WRITE_FROM_DICTATION"
    })
    void staticTaskTypesAreNotConfiguredHere(String taskType) {
        assertThatThrownBy(() -> config.timingFor(taskType))
                .isInstanceOf(TaskTimingNotConfiguredException.class);
        assertThat(config.timingForIfConfigured(taskType)).isNull();
    }

    @Test
    void timingForIfConfiguredNonThrowingLookupReturnsNullInsteadOfThrowing() {
        assertThat(config.timingForIfConfigured("MC_READING_SINGLE")).isNull();
        assertThat(config.timingForIfConfigured("PERSONAL_INTRODUCTION")).isNotNull();
    }
}
