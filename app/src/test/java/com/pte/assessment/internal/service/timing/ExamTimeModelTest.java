package com.pte.assessment.internal.service.timing;

import static org.assertj.core.api.Assertions.assertThat;

import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExamTimeModelTest {

    private static ExamDefinitionItemResponse item(String key, String section, int min, int max, int prep,
            int response) {
        return new ExamDefinitionItemResponse(key, section, 1, min, max, prep, response, "AI_SPEECH",
                BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private static PartTimeRange range(List<PartTimeRange> ranges, ExamTimePart part) {
        return ranges.stream().filter(candidate -> candidate.part() == part).findFirst().orElseThrow();
    }

    @Test
    void aQuestionTakesPrepPlusResponse() {
        List<PartTimeRange> ranges = ExamTimeModel.partRanges(
                List.of(item("READ_ALOUD", "SPEAKING", 6, 7, 35, 35)), Map.of());

        assertThat(range(ranges, ExamTimePart.SPEAKING_WRITING).minSeconds()).isEqualTo(6 * 70);
        assertThat(range(ranges, ExamTimePart.SPEAKING_WRITING).maxSeconds()).isEqualTo(7 * 70);
    }

    @Test
    void speakingAndWritingShareOnePart() {
        List<PartTimeRange> ranges = ExamTimeModel.partRanges(List.of(
                item("READ_ALOUD", "SPEAKING", 1, 1, 0, 40),
                item("WRITE_ESSAY", "WRITING", 1, 1, 0, 1200)), Map.of());

        assertThat(range(ranges, ExamTimePart.SPEAKING_WRITING).minSeconds()).isEqualTo(1240);
    }

    @Test
    void stimulusPlayedBeforeTheTimerIsAddedOnlyForTheTaskTypesListed() {
        List<ExamDefinitionItemResponse> items = List.of(
                item("RE_TELL_LECTURE", "SPEAKING", 2, 3, 10, 40),
                item("READ_ALOUD", "SPEAKING", 1, 1, 0, 40));

        List<PartTimeRange> ranges = ExamTimeModel.partRanges(items, Map.of("RE_TELL_LECTURE", 85));

        // Re-tell Lecture: 2..3 questions of (10 + 40 + 85); Read Aloud has no stimulus before the timer.
        assertThat(range(ranges, ExamTimePart.SPEAKING_WRITING).minSeconds()).isEqualTo(2 * 135 + 40);
        assertThat(range(ranges, ExamTimePart.SPEAKING_WRITING).maxSeconds()).isEqualTo(3 * 135 + 40);
    }

    @Test
    void everyPartIsReportedEvenWhenItHasNoTaskType() {
        List<PartTimeRange> ranges = ExamTimeModel.partRanges(
                List.of(item("MC_READING_SINGLE", "READING", 2, 3, 0, 90)), Map.of());

        assertThat(ranges).extracting(PartTimeRange::part).containsExactly(ExamTimePart.values());
        assertThat(range(ranges, ExamTimePart.LISTENING).maxSeconds()).isZero();
        assertThat(range(ranges, ExamTimePart.READING).minSeconds()).isEqualTo(180);
    }
}
