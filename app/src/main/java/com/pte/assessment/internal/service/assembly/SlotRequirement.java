package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.internal.service.timing.ExamTimePart;

/** One task type of a template as the planner sees it: how many questions it may have and how long each takes. */
public record SlotRequirement(String taskTypeKey, ExamTimePart part, int minCount, int maxCount,
        long secondsPerQuestion) {
}
