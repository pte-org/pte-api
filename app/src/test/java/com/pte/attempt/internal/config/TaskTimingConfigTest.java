package com.pte.attempt.internal.config;

import com.pte.attempt.internal.exception.TaskTimingNotConfiguredException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the real {@code config/task-timing.json} from the classpath (no
 * mocking — this is exactly what would catch a typo or missing entry in the
 * actual config file). Since Phase 3 (plans/score-template-exam-generation),
 * this file only carries: {@code PERSONAL_INTRODUCTION} (UNSCORED, not in
 * ScoreTemplate) and the 5 audio-prompt Speaking types' {@code
 * preListenSeconds} — every other task type's prep/response/scoringMethod
 * now lives in the pinned {@code ScoreTemplate} (see {@code SnapshotPinService}).
 */
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

    @ParameterizedTest(name = "{0} still carries only preListenSeconds (prep/response moved to ScoreTemplate)")
    @ValueSource(strings = {"REPEAT_SENTENCE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION", "RESPOND_TO_A_SITUATION",
            "SUMMARIZE_GROUP_DISCUSSION"})
    void audioPromptTypes_keepOnlyPreListenSeconds(String taskType) {
        TaskTimingConfig.Timing timing = config.timingFor(taskType);

        assertThat(timing.preListenSeconds()).isNotNull();
        assertThat(timing.preRecordSeconds()).isNull();
        // prepSeconds/responseSeconds keys were removed entirely — JsonNode.path()
        // on a missing key defaults .asInt() to 0, not an error.
        assertThat(timing.prepSeconds()).isZero();
        assertThat(timing.responseSeconds()).isZero();
    }

    @Test
    void repeatSentenceAndAnswerShortQuestion_keepTheirNoPreparationBeepValue() {
        assertThat(config.timingFor("REPEAT_SENTENCE").preListenSeconds()).isEqualTo(3);
        assertThat(config.timingFor("ANSWER_SHORT_QUESTION").preListenSeconds()).isEqualTo(3);
    }

    @ParameterizedTest(name = "{0} moved entirely to ScoreTemplate — no JSON entry left")
    @ValueSource(strings = {
            "READ_ALOUD", "DESCRIBE_IMAGE", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
            "FILL_IN_THE_BLANKS_DROPDOWN", "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE",
            "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN", "HIGHLIGHT_CORRECT_SUMMARY",
            "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION"
    })
    void seventeenStaticTaskTypes_noLongerConfiguredHere(String taskType) {
        assertThatThrownBy(() -> config.timingFor(taskType)).isInstanceOf(TaskTimingNotConfiguredException.class);
        assertThat(config.timingForIfConfigured(taskType)).isNull();
    }

    @Test
    void timingForIfConfigured_nonThrowingLookup_returnsNullInsteadOfThrowing() {
        assertThat(config.timingForIfConfigured("MC_READING_SINGLE")).isNull();
        assertThat(config.timingForIfConfigured("PERSONAL_INTRODUCTION")).isNotNull();
    }
}
