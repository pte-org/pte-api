package com.pte.assessment.internal.service.timing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pte.assessment.domain.ExamTimeFrame;
import com.pte.assessment.dto.request.UpdateExamTimeFrameRequest;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;
import org.junit.jupiter.api.Test;

class ExamTimeFrameRulesTest {

    private static UpdateExamTimeFrameRequest request(int swMin, int swMax, int readingMin, int readingMax,
            int listeningMin, int listeningMax, int totalMax) {
        return new UpdateExamTimeFrameRequest(swMin, swMax, readingMin, readingMax, listeningMin, listeningMax,
                totalMax);
    }

    @Test
    void acceptsThePearsonWindow() {
        ExamTimeFrame frame = ExamTimeFrameRules.fromRequest(request(4560, 5040, 1380, 1800, 1860, 2340, 8100));

        assertThat(frame.speakingWritingMinSeconds()).isEqualTo(4560);
        assertThat(frame.totalMaxSeconds()).isEqualTo(8100);
    }

    @Test
    void rejectsAPartWhoseMinimumExceedsItsMaximum() {
        assertThatThrownBy(() -> ExamTimeFrameRules.fromRequest(request(5040, 4560, 1380, 1800, 1860, 2340, 9999)))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessage(ExamDefinitionConstants.TIME_FRAME_INVALID_RANGE
                        + ExamDefinitionConstants.TIME_FRAME_PART_SPEAKING_WRITING);
    }

    @Test
    void rejectsACeilingBelowTheSumOfThePartMinimums() {
        assertThatThrownBy(() -> ExamTimeFrameRules.fromRequest(request(4560, 5040, 1380, 1800, 1860, 2340, 7000)))
                .isInstanceOf(ExamDefinitionValidationException.class)
                .hasMessage(ExamDefinitionConstants.TIME_FRAME_TOTAL_BELOW_MINIMUM);
    }

    @Test
    void acceptsACeilingEqualToTheSumOfTheMinimums() {
        ExamTimeFrame frame = ExamTimeFrameRules.fromRequest(
                request(4560, 5040, 1380, 1800, 1860, 2340, 4560 + 1380 + 1860));

        assertThat(frame.totalMaxSeconds()).isEqualTo(7800);
    }
}
