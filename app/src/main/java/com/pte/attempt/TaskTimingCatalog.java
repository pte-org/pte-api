package com.pte.attempt;

import com.pte.attempt.internal.config.TaskTimingConfig;
import org.springframework.stereotype.Service;

import java.util.OptionalInt;

/**
 * Public read-only view of the client sub-stage timing in {@code config/task-timing.json} for other modules.
 * Practice uses it to pin the same {@code preListenSeconds} the exam uses for the audio-prompt Speaking types,
 * so the value lives in one file and is never copied.
 */
@Service
public class TaskTimingCatalog {

    private final TaskTimingConfig taskTimingConfig;

    public TaskTimingCatalog(TaskTimingConfig taskTimingConfig) {
        this.taskTimingConfig = taskTimingConfig;
    }

    /** Seconds before the prompt audio plays, or empty when the task type has no such stage. */
    public OptionalInt preListenSecondsFor(String taskTypeCode) {
        TaskTimingConfig.Timing timing = taskTimingConfig.timingForIfConfigured(taskTypeCode);
        return timing == null || timing.preListenSeconds() == null
                ? OptionalInt.empty() : OptionalInt.of(timing.preListenSeconds());
    }
}
