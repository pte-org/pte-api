package com.pte.attempt.internal.config;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.TaskTimingNotConfiguredException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads per-task-type prep/response timing from {@code config/task-timing.json}
 * (config-driven, matching itembank's skill-mapping pattern). Only the task
 * types actually delivered are configured — an unconfigured type fails fast at
 * pin time rather than silently defaulting (server is the timing source of
 * truth, so a wrong default is a real correctness bug, not a cosmetic gap).
 */
@Component
public class TaskTimingConfig {

    private static final String RESOURCE = "config/task-timing.json";

    private final Map<String, Timing> timings = new HashMap<>();

    public TaskTimingConfig(JsonMapper jsonMapper) {
        load(jsonMapper);
    }

    public Timing timingFor(String taskType) {
        Timing timing = timings.get(taskType);
        if (timing == null) {
            throw new TaskTimingNotConfiguredException();
        }
        return timing;
    }

    /**
     * Non-throwing lookup — {@code null} if this task type has no entry here
     * at all. Since Phase 3, that's every task type except {@code
     * PERSONAL_INTRODUCTION} and the 5 audio-prompt Speaking types (everyone
     * else's timing now lives in the pinned {@code ScoreTemplate}); {@code
     * SnapshotPinService} uses this instead of {@link #timingFor} whenever a
     * task type IS in the pinned template, so a template-only static type
     * (e.g. {@code MC_READING_SINGLE}) never trips {@link
     * TaskTimingNotConfiguredException} just because it's absent from this file.
     */
    public Timing timingForIfConfigured(String taskType) {
        return timings.get(taskType);
    }

    private void load(JsonMapper jsonMapper) {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            JsonNode root = jsonMapper.readTree(in).path("timings");
            root.propertyNames().forEach(taskType -> {
                JsonNode node = root.get(taskType);
                timings.put(taskType, new Timing(
                        node.path("prepSeconds").asInt(),
                        node.path("responseSeconds").asInt(),
                        node.has("preListenSeconds") ? node.path("preListenSeconds").asInt() : null,
                        node.has("preRecordSeconds") ? node.path("preRecordSeconds").asInt() : null));
            });
        } catch (IOException | JacksonException ex) {
            throw new IllegalStateException(String.format(AttemptConstants.TASK_TIMING_LOAD_FAILED, RESOURCE), ex);
        }
    }

    /**
     * {@code prepSeconds} stays the static per-type fallback for every task
     * type — for the 5 audio-prompt Speaking types it becomes dead/unused once
     * {@code SnapshotPinService} computes prep dynamically from real audio
     * duration instead. {@code preListenSeconds}/{@code preRecordSeconds} are
     * {@code null} for every task type except those 5 — their presence (not
     * the task type name) is what a pinning call actually checks.
     */
    public record Timing(int prepSeconds, int responseSeconds, Integer preListenSeconds, Integer preRecordSeconds) {
    }
}
