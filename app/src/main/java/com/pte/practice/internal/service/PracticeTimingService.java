package com.pte.practice.internal.service;

import com.pte.attempt.TaskTimingCatalog;
import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.scoretemplate.ScoreTemplateService;
import com.pte.scoretemplate.dto.response.ScoreTemplateItemResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * Timed practice uses the same timing as the exam: prep/response from the active Score Template and
 * {@code preListenSeconds} from {@code config/task-timing.json}. No practice-only copy of the numbers exists, so
 * changing the template is the only way to change practice timing.
 */
@Service
public class PracticeTimingService {

    private static final Logger log = LoggerFactory.getLogger(PracticeTimingService.class);

    private final ScoreTemplateService scoreTemplateService;
    private final TaskTimingCatalog taskTimingCatalog;

    public PracticeTimingService(ScoreTemplateService scoreTemplateService, TaskTimingCatalog taskTimingCatalog) {
        this.scoreTemplateService = scoreTemplateService;
        this.taskTimingCatalog = taskTimingCatalog;
    }

    /**
     * Timing per normalized task type code; empty when no template is active. {@code preRecordSeconds} is the
     * template's prep column (the gap before recording), not the exam's derived {@code PinnedItem.prepSeconds}.
     */
    public Map<String, Timing> activeTimings() {
        var active = scoreTemplateService.findActive();
        if (active.isEmpty()) {
            log.warn("No active Score Template; timed practice is unavailable");
            return Map.of();
        }
        Map<String, Timing> timings = new HashMap<>();
        for (ScoreTemplateItemResponse item : active.get().items()) {
            String code = normalize(item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey());
            if (code == null) {
                continue;
            }
            OptionalInt preListen = taskTimingCatalog.preListenSecondsFor(code);
            timings.putIfAbsent(code, new Timing(preListen.isPresent() ? preListen.getAsInt() : null,
                    item.prepSeconds(), item.responseSeconds()));
        }
        return Map.copyOf(timings);
    }

    /** True when every task code has a timing row, so a TIMED session can pace all of its items. */
    public boolean covers(Map<String, Timing> timings, Collection<String> taskCodes) {
        return !timings.isEmpty() && taskCodes.stream().allMatch(code -> timings.containsKey(normalize(code)));
    }

    public void requireTimedAvailable(Collection<String> taskCodes) {
        if (!covers(activeTimings(), taskCodes)) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_TIMED_MODE_UNAVAILABLE,
                    PracticeConstants.PRACTICE_TIMED_MODE_UNAVAILABLE_MESSAGE);
        }
    }

    public static String normalize(String taskCode) {
        return taskCode == null || taskCode.isBlank() ? null : TaskTypeCodeCompatibility.normalizeTaskTypeKey(taskCode);
    }

    public record Timing(Integer preListenSeconds, int preRecordSeconds, int responseSeconds) {
    }
}
