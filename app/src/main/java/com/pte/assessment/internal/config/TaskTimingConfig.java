package com.pte.assessment.internal.config;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.assessment.internal.exception.content.TaskTimingNotConfiguredException;
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
 * Shared read-only fallback timing source for task types that are not present
 * in the pinned exam definition. The assessment module owns this source so
 * host preview and student delivery resolve the same values.
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

    /** Returns {@code null} when the JSON has no entry for this task type. */
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
            throw new IllegalStateException(String.format(AssessmentConstants.TASK_TIMING_LOAD_FAILED, RESOURCE), ex);
        }
    }

    public record Timing(int prepSeconds, int responseSeconds, Integer preListenSeconds, Integer preRecordSeconds) {
    }
}
