package com.pte.assessment.internal.service.standard;

import static org.assertj.core.api.Assertions.assertThat;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import com.pte.assessment.dto.response.ExamDefinitionResponse;
import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HostTemplateStandardCheckTest {

    private static final ExamTimeFrameResponse FRAME = new ExamTimeFrameResponse(100, 400, 100, 400, 100, 400, 2000);

    private static ExamDefinitionItemResponse item(String key, String section, int min, int max, int prep,
            int response, String overallWeight) {
        return new ExamDefinitionItemResponse(key, section, 1, min, max, prep, response, "OBJECTIVE",
                new BigDecimal(overallWeight), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static ExamDefinitionResponse template(ExamTimeFrameResponse frame, ExamDefinitionItemResponse... items) {
        return new ExamDefinitionResponse(UUID.randomUUID(), "T", 1, "T", "ACTIVE", null, List.of(items),
                "STANDARD_PTE", null, null, null, null, null, null, frame);
    }

    private static ExamDefinitionResponse base() {
        return template(FRAME,
                item("READ_ALOUD", "SPEAKING", 2, 4, 10, 40, "3.50"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00"));
    }

    private static StandardCheckResult check(ExamDefinitionResponse host) {
        return HostTemplateStandardCheck.evaluate(host, base(), Map.of());
    }

    @Test
    void anUnchangedCopyIsStandard() {
        StandardCheckResult result = check(base());

        assertThat(result.standard()).isTrue();
        assertThat(result.deviations()).isEmpty();
    }

    @Test
    void aNarrowerCountRangeInsideTheOriginalIsStillStandard() {
        StandardCheckResult result = check(template(null,
                item("READ_ALOUD", "SPEAKING", 3, 3, 10, 40, "3.50"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")));

        assertThat(result.standard()).isTrue();
    }

    @Test
    void droppingATaskTypeLosesTheLabel() {
        StandardCheckResult result = check(template(null,
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")));

        assertThat(result.deviations()).contains(HostTemplateStandardCheck.MISSING_TASK_TYPE + "READ_ALOUD");
    }

    @Test
    void changingTheTimingLosesTheLabel() {
        StandardCheckResult result = check(template(null,
                item("READ_ALOUD", "SPEAKING", 2, 4, 10, 60, "3.50"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")));

        assertThat(result.deviations()).contains(HostTemplateStandardCheck.TIMING_CHANGED + "READ_ALOUD");
    }

    @Test
    void aCountRangeWiderThanTheOriginalLosesTheLabel() {
        StandardCheckResult result = check(template(null,
                item("READ_ALOUD", "SPEAKING", 1, 4, 10, 40, "3.50"),
                item("MC_READING_SINGLE", "READING", 2, 5, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")));

        assertThat(result.deviations()).contains(HostTemplateStandardCheck.COUNT_OUTSIDE_STANDARD + "READ_ALOUD",
                HostTemplateStandardCheck.COUNT_OUTSIDE_STANDARD + "MC_READING_SINGLE");
    }

    @Test
    void changingAWeightLosesTheLabelButAScaleDifferenceDoesNot() {
        StandardCheckResult changed = check(template(null,
                item("READ_ALOUD", "SPEAKING", 2, 4, 10, 40, "4.00"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")));
        StandardCheckResult sameWeight = check(template(null,
                item("READ_ALOUD", "SPEAKING", 2, 4, 10, 40, "3.5"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.0"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.0")));

        assertThat(changed.deviations()).contains(HostTemplateStandardCheck.WEIGHTS_CHANGED + "READ_ALOUD");
        assertThat(sameWeight.standard()).isTrue();
    }

    @Test
    void countsThatCannotFitTheOriginalsTimeWindowsLoseTheLabel() {
        // Reading is fixed at 2 questions of 90 s = 180 s, inside 100..400; Speaking at 4 x 50 s = 200 s too.
        // A Listening window of 100..400 cannot be reached with 20 questions of 90 s (1800 s).
        StandardCheckResult result = HostTemplateStandardCheck.evaluate(template(null,
                item("READ_ALOUD", "SPEAKING", 2, 4, 10, 40, "3.50"),
                item("MC_READING_SINGLE", "READING", 2, 4, 0, 90, "1.00"),
                item("MC_LISTENING_SINGLE", "LISTENING", 2, 4, 0, 90, "1.00")), base(), Map.of("READ_ALOUD", 1000));

        assertThat(result.deviations()).contains(HostTemplateStandardCheck.TIME_FRAME_INFEASIBLE + "SPEAKING_WRITING");
    }
}
