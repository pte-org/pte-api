package com.pte.assessment.internal.service.standard;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.PartTimeFeasibilityResponse;
import com.pte.assessment.internal.service.timing.ExamTimeModel;
import com.pte.assessment.internal.service.timing.PartTimeReport;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides whether a host template is still "Pearson standard": a template is free to change, but it keeps the
 * label only while it holds every task type of the platform template it was cloned from with the same timing,
 * a count range inside the original, the same weights, and counts that can still fit the original's time
 * windows. The order of the task types does not matter, since exams always put them in the standard order.
 */
public final class HostTemplateStandardCheck {

    public static final String MISSING_TASK_TYPE = "MISSING_TASK_TYPE:";
    public static final String TIMING_CHANGED = "TIMING_CHANGED:";
    public static final String COUNT_OUTSIDE_STANDARD = "COUNT_OUTSIDE_STANDARD:";
    public static final String WEIGHTS_CHANGED = "WEIGHTS_CHANGED:";
    public static final String TIME_FRAME_INFEASIBLE = "TIME_FRAME_INFEASIBLE:";

    private HostTemplateStandardCheck() {
    }

    public static StandardCheckResult evaluate(ExamDefinitionResponse host, ExamDefinitionResponse base,
            Map<String, Integer> stimulusBeforeTimerSeconds) {
        Map<String, ExamDefinitionItemResponse> hostItems = byKey(host);
        List<String> deviations = new ArrayList<>();
        for (ExamDefinitionItemResponse baseItem : base.items()) {
            ExamDefinitionItemResponse hostItem = hostItems.get(baseItem.taskTypeKey());
            if (hostItem == null) {
                deviations.add(MISSING_TASK_TYPE + baseItem.taskTypeKey());
                continue;
            }
            compareItem(baseItem, hostItem, deviations);
        }
        if (base.timeFrame() != null) {
            PartTimeReport.of(ExamTimeModel.partRanges(host.items(), stimulusBeforeTimerSeconds), base.timeFrame())
                    .stream().filter(part -> Boolean.FALSE.equals(part.canFitFrame()))
                    .map(PartTimeFeasibilityResponse::part)
                    .forEach(part -> deviations.add(TIME_FRAME_INFEASIBLE + part));
        }
        return new StandardCheckResult(deviations.isEmpty(), List.copyOf(deviations));
    }

    private static void compareItem(ExamDefinitionItemResponse base, ExamDefinitionItemResponse host,
            List<String> deviations) {
        String key = base.taskTypeKey();
        if (host.prepSeconds() != base.prepSeconds() || host.responseSeconds() != base.responseSeconds()) {
            deviations.add(TIMING_CHANGED + key);
        }
        if (host.minCount() < base.minCount() || host.maxCount() > base.maxCount()) {
            deviations.add(COUNT_OUTSIDE_STANDARD + key);
        }
        if (!sameWeight(host.overallWeight(), base.overallWeight())
                || !sameWeight(host.speakingWeight(), base.speakingWeight())
                || !sameWeight(host.writingWeight(), base.writingWeight())
                || !sameWeight(host.readingWeight(), base.readingWeight())
                || !sameWeight(host.listeningWeight(), base.listeningWeight())) {
            deviations.add(WEIGHTS_CHANGED + key);
        }
    }

    // compareTo, not equals: 4.0 and 4.00 are the same weight.
    private static boolean sameWeight(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) == 0;
    }

    private static Map<String, ExamDefinitionItemResponse> byKey(ExamDefinitionResponse template) {
        Map<String, ExamDefinitionItemResponse> items = new LinkedHashMap<>();
        template.items().forEach(item -> items.put(item.taskTypeKey(), item));
        return items;
    }
}
