package com.pte.assessment.internal.service.timing;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Planned duration of a template, by Pearson part.
 *
 * <p>A question takes {@code prep + response}, plus the time its stimulus plays before the answer timer
 * starts. Listening answer times already include the audio, so only task types whose stimulus plays first
 * appear in {@code stimulusBeforeTimerSeconds}. The exam clock itself still counts {@code prep + response};
 * this model is for planning and reporting.
 */
public final class ExamTimeModel {

    private ExamTimeModel() {
    }

    /** One entry per part, in {@link ExamTimePart} order, including parts with no task type (zero seconds). */
    public static List<PartTimeRange> partRanges(List<ExamDefinitionItemResponse> items,
            Map<String, Integer> stimulusBeforeTimerSeconds) {
        Map<ExamTimePart, long[]> totals = new EnumMap<>(ExamTimePart.class);
        for (ExamTimePart part : ExamTimePart.values()) {
            totals.put(part, new long[2]);
        }
        for (ExamDefinitionItemResponse item : items) {
            long perQuestion = questionSeconds(item, stimulusBeforeTimerSeconds);
            long[] total = totals.get(ExamTimePart.ofSection(item.section()));
            total[0] += item.minCount() * perQuestion;
            total[1] += item.maxCount() * perQuestion;
        }
        return totals.entrySet().stream()
                .map(entry -> new PartTimeRange(entry.getKey(), entry.getValue()[0], entry.getValue()[1]))
                .toList();
    }

    static long questionSeconds(ExamDefinitionItemResponse item, Map<String, Integer> stimulusBeforeTimerSeconds) {
        int stimulus = stimulusBeforeTimerSeconds.getOrDefault(item.taskTypeKey(), 0);
        return (long) item.prepSeconds() + item.responseSeconds() + stimulus;
    }
}
