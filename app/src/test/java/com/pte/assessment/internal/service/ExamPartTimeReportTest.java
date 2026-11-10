package com.pte.assessment.internal.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.dto.response.PartTimeFeasibilityResponse;
import com.pte.assessment.internal.service.timing.ExamTimePart;
import com.pte.assessment.internal.service.timing.PartTimeRange;
import com.pte.assessment.internal.service.timing.PartTimeReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExamPartTimeReportTest {

    private static final ExamTimeFrameResponse PEARSON =
            new ExamTimeFrameResponse(4560, 5040, 1380, 1800, 1860, 2340, 8100);

    private static PartTimeFeasibilityResponse report(PartTimeRange range, ExamTimeFrameResponse frame) {
        return PartTimeReport.of(List.of(range), frame).get(0);
    }

    @Test
    void aRangeThatOverlapsTheWindowCanFitIt() {
        PartTimeFeasibilityResponse listening = report(new PartTimeRange(ExamTimePart.LISTENING, 2070, 2790), PEARSON);

        assertThat(listening.canFitFrame()).isTrue();
        assertThat(listening.frameMinSeconds()).isEqualTo(1860);
        assertThat(listening.frameMaxSeconds()).isEqualTo(2340);
    }

    @Test
    void aRangeEntirelyBelowTheWindowCannotFitIt() {
        PartTimeFeasibilityResponse speaking =
                report(new PartTimeRange(ExamTimePart.SPEAKING_WRITING, 3800, 4200), PEARSON);

        assertThat(speaking.canFitFrame()).isFalse();
    }

    @Test
    void aRangeEntirelyAboveTheWindowCannotFitIt() {
        PartTimeFeasibilityResponse reading = report(new PartTimeRange(ExamTimePart.READING, 1900, 2200), PEARSON);

        assertThat(reading.canFitFrame()).isFalse();
    }

    @Test
    void withoutAFrameThereIsNothingToFit() {
        PartTimeFeasibilityResponse reading = report(new PartTimeRange(ExamTimePart.READING, 1680, 2220), null);

        assertThat(reading.canFitFrame()).isNull();
        assertThat(reading.frameMinSeconds()).isNull();
        assertThat(reading.minSeconds()).isEqualTo(1680);
    }
}
