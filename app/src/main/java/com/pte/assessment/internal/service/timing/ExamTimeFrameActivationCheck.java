package com.pte.assessment.internal.service.timing;

import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.PartTimeFeasibilityResponse;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Refuses to activate a template whose counts cannot put some part inside its time window. The total ceiling is
 * not checked: answer times listed as "up to N minutes" are stored at their upper bound, so a plan can exceed the
 * ceiling on paper while real time stays under it (see {@code BalancedCountPlanner}).
 */
public final class ExamTimeFrameActivationCheck {

    private static final double SECONDS_PER_MINUTE = 60.0;

    private ExamTimeFrameActivationCheck() {
    }

    public static void verify(ExamDefinitionResponse template, Map<String, Integer> stimulusBeforeTimerSeconds) {
        if (template.timeFrame() == null) {
            return;
        }
        List<PartTimeFeasibilityResponse> parts = PartTimeReport.of(
                ExamTimeModel.partRanges(template.items(), stimulusBeforeTimerSeconds), template.timeFrame());
        String cannotFit = parts.stream()
                .filter(part -> Boolean.FALSE.equals(part.canFitFrame()))
                .map(ExamTimeFrameActivationCheck::describe)
                .collect(Collectors.joining("; "));
        if (!cannotFit.isEmpty()) {
            throw new ExamDefinitionValidationException(
                    ExamDefinitionConstants.TEMPLATE_TIME_FRAME_INFEASIBLE + cannotFit);
        }
    }

    private static String describe(PartTimeFeasibilityResponse part) {
        return String.format(Locale.ROOT, "%s plans %.1f-%.1f min but its window is %.1f-%.1f min", part.part(),
                part.minSeconds() / SECONDS_PER_MINUTE, part.maxSeconds() / SECONDS_PER_MINUTE,
                part.frameMinSeconds() / SECONDS_PER_MINUTE, part.frameMaxSeconds() / SECONDS_PER_MINUTE);
    }
}
