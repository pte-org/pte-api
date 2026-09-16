package com.pte.attempt.internal.config;

import com.pte.attempt.internal.exception.TaskTimingNotConfiguredException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Ported from services/exam-delivery's own TaskTimingConfigTest — same
 * coverage, new package. Loads the real {@code config/task-timing.json} from
 * the classpath (no mocking — this is exactly what would catch a typo or
 * missing entry in the actual config file).
 */
class TaskTimingConfigTest {

    private final TaskTimingConfig config = new TaskTimingConfig(JsonMapper.builder().build());

    @Test
    void describeImageIsConfiguredWithStaticPrepAndResponseSeconds() {
        TaskTimingConfig.Timing timing = config.timingFor("DESCRIBE_IMAGE");

        assertThat(timing.prepSeconds()).isEqualTo(25);
        assertThat(timing.responseSeconds()).isEqualTo(40);
        assertThat(timing.preListenSeconds()).isNull();
        assertThat(timing.preRecordSeconds()).isNull();
    }

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

    @ParameterizedTest(name = "{0} has a pinning placeholder")
    @ValueSource(strings = {
            "SUMMARIZE_SPOKEN_TEXT",
            "MC_LISTENING_MULTIPLE",
            "FILL_BLANKS_LISTENING",
            "HIGHLIGHT_CORRECT_SUMMARY",
            "SELECT_MISSING_WORD",
            "HIGHLIGHT_INCORRECT_WORDS",
            "WRITE_FROM_DICTATION"
    })
    void sevenListeningTypesAreConfiguredForSnapshotPinning(String taskType) {
        assertThatCode(() -> config.timingFor(taskType)).doesNotThrowAnyException();
    }
}
