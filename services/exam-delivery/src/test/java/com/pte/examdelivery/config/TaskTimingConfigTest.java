package com.pte.examdelivery.config;

import com.pte.examdelivery.domain.exception.TaskTimingNotConfiguredException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Loads the real {@code config/task-timing.json} from the classpath (no mocking —
 * this is exactly what would catch a typo or missing entry in the actual config
 * file). Covers the 2 gaps closed by plans/phat-describe-image-e2e:
 * DESCRIBE_IMAGE and PERSONAL_INTRODUCTION previously had no entry at all,
 * so timingFor() threw TaskTimingNotConfiguredException for both.
 */
class TaskTimingConfigTest {

    private final TaskTimingConfig config = new TaskTimingConfig(JsonMapper.builder().build());

    @Test
    void describeImageIsConfiguredWithStaticPrepAndResponseSeconds() {
        TaskTimingConfig.Timing timing = config.timingFor("DESCRIBE_IMAGE");

        assertThat(timing.prepSeconds()).isEqualTo(25);
        assertThat(timing.responseSeconds()).isEqualTo(40);
        // No sub-stage split — DESCRIBE_IMAGE isn't one of the 5 audio-prompt
        // Speaking types, so these must stay null (config presence, not the
        // task-type name, is what drives SnapshotPinService's dynamic-vs-static
        // branch).
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
}
