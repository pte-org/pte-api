package com.pte.examdelivery.config;

import com.pte.examdelivery.domain.exception.TaskTimingNotConfiguredException;
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
 * (config-driven, matching authoring's skill-mapping pattern). Only the task
 * types actually delivered in Milestone 1 are configured — an unconfigured type
 * fails fast at pin time rather than silently defaulting (server is the timing
 * source of truth, so a wrong default is a real correctness bug, not a
 * cosmetic gap).
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
            throw new IllegalStateException("Failed to load task timing from " + RESOURCE, ex);
        }
    }

    /**
     * {@code prepSeconds} stays the static per-type fallback for every task
     * type — for the 5 audio-prompt Speaking types it becomes dead/unused
     * once {@code SnapshotPinService} computes prep dynamically from real
     * audio duration instead (left in place rather than removed, so this
     * config's shape doesn't change for an unrelated reason). {@code
     * preListenSeconds}/{@code preRecordSeconds} are {@code null} for every
     * task type except those 5, which is exactly what makes the dynamic-vs-
     * static branch in {@code SnapshotPinService} correct — their presence
     * (not the task type name) is what a pinning call actually checks
     * (plans/phat-speaking-dynamic-prep-timing).
     */
    public record Timing(int prepSeconds, int responseSeconds, Integer preListenSeconds, Integer preRecordSeconds) {
    }
}
