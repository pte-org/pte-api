package com.pte.assessment.internal.service.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.internal.service.timing.ExamTimePart;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BalancedCountPlannerTest {

    private static final ExamTimeFrameResponse PEARSON =
            new ExamTimeFrameResponse(4560, 5040, 1380, 1800, 1860, 2340, 8100);

    private static SlotRequirement slot(String key, ExamTimePart part, int min, int max, long seconds) {
        return new SlotRequirement(key, part, min, max, seconds);
    }

    /** The standard template: counts and answer times of the APEUni V5 table, plus the audio played before the timer. */
    private static List<SlotRequirement> standardTemplate() {
        ExamTimePart sw = ExamTimePart.SPEAKING_WRITING;
        ExamTimePart reading = ExamTimePart.READING;
        ExamTimePart listening = ExamTimePart.LISTENING;
        return List.of(
                slot("READ_ALOUD", sw, 6, 7, 70), slot("REPEAT_SENTENCE", sw, 10, 12, 21),
                slot("DESCRIBE_IMAGE", sw, 5, 6, 65), slot("RE_TELL_LECTURE", sw, 2, 3, 135),
                slot("ANSWER_SHORT_QUESTION", sw, 5, 6, 15), slot("SUMMARIZE_GROUP_DISCUSSION", sw, 2, 3, 216),
                slot("RESPOND_TO_A_SITUATION", sw, 2, 3, 68), slot("SUMMARIZE_WRITTEN_TEXT", sw, 2, 2, 600),
                slot("WRITE_ESSAY", sw, 1, 1, 1200),
                slot("FILL_IN_THE_BLANKS_DROPDOWN", reading, 5, 6, 120), slot("MC_READING_MULTIPLE", reading, 2, 3, 90),
                slot("RE_ORDER_PARAGRAPHS", reading, 2, 3, 120),
                slot("FILL_IN_THE_BLANKS_DRAG_AND_DROP", reading, 4, 5, 120),
                slot("MC_READING_SINGLE", reading, 2, 3, 90),
                slot("SUMMARIZE_SPOKEN_TEXT", listening, 1, 1, 600), slot("MC_LISTENING_MULTIPLE", listening, 2, 3, 90),
                slot("FILL_IN_THE_BLANKS_TYPE_IN", listening, 2, 3, 120),
                slot("HIGHLIGHT_CORRECT_SUMMARY", listening, 2, 3, 90),
                slot("MC_LISTENING_SINGLE", listening, 2, 3, 90), slot("SELECT_MISSING_WORD", listening, 1, 2, 90),
                slot("HIGHLIGHT_INCORRECT_WORDS", listening, 2, 3, 120),
                slot("WRITE_FROM_DICTATION", listening, 3, 4, 120));
    }

    @Test
    void everyPartLandsInsideItsWindow() {
        for (long seed = 0; seed < 2000; seed++) {
            PlanResult plan = BalancedCountPlanner.plan(standardTemplate(), PEARSON, seed);

            assertThat(plan.isFeasible()).isTrue();
            assertThat(plan.partSeconds().get(ExamTimePart.SPEAKING_WRITING)).isBetween(4560L, 5040L);
            assertThat(plan.partSeconds().get(ExamTimePart.READING)).isBetween(1380L, 1800L);
            assertThat(plan.partSeconds().get(ExamTimePart.LISTENING)).isBetween(1860L, 2340L);
        }
    }

    @Test
    void everyTaskTypeStaysInsideItsCountRange() {
        List<SlotRequirement> slots = standardTemplate();
        for (long seed = 0; seed < 500; seed++) {
            PlanResult plan = BalancedCountPlanner.plan(slots, PEARSON, seed);

            for (SlotRequirement slot : slots) {
                assertThat(plan.counts().get(slot.taskTypeKey())).isBetween(slot.minCount(), slot.maxCount());
            }
        }
    }

    @Test
    void neverPicksTheAllMinimumOrAllMaximumExtreme() {
        List<SlotRequirement> slots = standardTemplate();
        for (long seed = 0; seed < 2000; seed++) {
            PlanResult plan = BalancedCountPlanner.plan(slots, PEARSON, seed);

            boolean allMinimum = slots.stream().allMatch(slot -> plan.counts().get(slot.taskTypeKey()) == slot.minCount());
            boolean allMaximum = slots.stream().allMatch(slot -> plan.counts().get(slot.taskTypeKey()) == slot.maxCount());
            assertThat(allMinimum || allMaximum).isFalse();
        }
    }

    @Test
    void sameSeedGivesTheSamePlanAndDifferentSeedsVary() {
        List<SlotRequirement> slots = standardTemplate();

        assertThat(BalancedCountPlanner.plan(slots, PEARSON, 42).counts())
                .isEqualTo(BalancedCountPlanner.plan(slots, PEARSON, 42).counts());
        Set<Object> distinctPlans = new HashSet<>();
        for (long seed = 0; seed < 300; seed++) {
            distinctPlans.add(BalancedCountPlanner.plan(slots, PEARSON, seed).counts());
        }
        assertThat(distinctPlans.size()).isGreaterThan(20);
    }

    @Test
    void doesNotCrowdIntoTheShortEdgeOfAWindow() {
        // Many combinations of counts give the shortest durations and few give the middle ones. Weighting each
        // distinct duration equally keeps the plans spread over the window instead of piling up at its low end.
        double speakingWritingTotal = 0;
        int plans = 500;
        for (long seed = 0; seed < plans; seed++) {
            speakingWritingTotal += BalancedCountPlanner.plan(standardTemplate(), PEARSON, seed)
                    .partSeconds().get(ExamTimePart.SPEAKING_WRITING);
        }

        double averageMinutes = speakingWritingTotal / plans / 60.0;
        // The reachable part of the 76-84 minute window is 76 to about 81.3; its middle is about 78.7.
        assertThat(averageMinutes).isGreaterThan(78.0);
    }

    @Test
    void reportsThePartWhoseWindowCannotBeReached() {
        ExamTimeFrameResponse impossible = new ExamTimeFrameResponse(90000, 99000, 1380, 1800, 1860, 2340, 200000);

        PlanResult plan = BalancedCountPlanner.plan(standardTemplate(), impossible, 1);

        assertThat(plan.isFeasible()).isFalse();
        assertThat(plan.counts()).isEmpty();
        assertThat(plan.failures()).extracting(PlanResult.PartFailure::part)
                .containsExactly(ExamTimePart.SPEAKING_WRITING);
    }

    @Test
    void ignoresTheTotalCeilingWhenNoPlanInsideTheWindowsStaysUnderIt() {
        // The windows alone need at least 4560 + 1380 + 1860 = 7800 s; with the answer-time caps the standard
        // template needs more, so a 135-minute ceiling cannot be honoured.
        PlanResult plan = BalancedCountPlanner.plan(standardTemplate(), PEARSON, 7);

        assertThat(plan.isFeasible()).isTrue();
        assertThat(plan.totalCeilingMet()).isFalse();
        assertThat(plan.totalSeconds()).isGreaterThan(8100);
    }

    @Test
    void honoursTheTotalCeilingWhenAPlanUnderItExists() {
        ExamTimeFrameResponse roomy = new ExamTimeFrameResponse(4560, 5040, 1380, 1800, 1860, 2340, 9600);
        for (long seed = 0; seed < 300; seed++) {
            PlanResult plan = BalancedCountPlanner.plan(standardTemplate(), roomy, seed);

            assertThat(plan.totalCeilingMet()).isTrue();
            assertThat(plan.totalSeconds()).isLessThanOrEqualTo(9600);
        }
    }

    @Test
    void aTemplateWithFixedCountsStillGetsAPlan() {
        List<SlotRequirement> fixed = List.of(slot("A", ExamTimePart.READING, 3, 3, 500));
        ExamTimeFrameResponse frame = new ExamTimeFrameResponse(1, 2, 1000, 2000, 1, 2, 5000);

        PlanResult plan = BalancedCountPlanner.plan(fixed, frame, 1);

        assertThat(plan.isFeasible()).isTrue();
        assertThat(plan.counts()).containsEntry("A", 3);
    }
}
