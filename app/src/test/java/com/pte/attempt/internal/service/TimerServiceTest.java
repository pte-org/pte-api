package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.service.cache.ExamItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ported from services/exam-delivery's own TimerServiceTest — same coverage,
 * new package. Covers the section-scoped vs task-scoped budget split (READING
 * shares one budget across every task in the section, summed from all its
 * items; everything else keeps the original per-task values).
 */
class TimerServiceTest {

    private final TimerService timerService = new TimerService(com.pte.TestClocks.fixed());
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        attempt = new ExamAttempt();
    }

    private ExamItemView readingItem(int orderIndex, int responseSeconds) {
        return FakeExam.view(orderIndex, "READING", "MC_READING_SINGLE", 0, responseSeconds);
    }

    private ExamItemView taskScopedItem(int orderIndex, String section, String taskType, int prepSeconds,
                                           int responseSeconds) {
        return FakeExam.view(orderIndex, section, taskType, prepSeconds, responseSeconds);
    }

    @Test
    void startTask_setsCurrentOrderIndex_andResetsReplayCounters() {
        ExamItemView item = taskScopedItem(2, "SPEAKING", "READ_ALOUD", 35, 40);
        attempt.setPlayCount(3);
        attempt.setLastPlayRequestId("stale-request-id");
        attempt.setLastPlayAllowed(false);

        timerService.startTask(attempt, item, List.of(item));

        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(2);
        assertThat(attempt.getPlayCount()).isZero();
        assertThat(attempt.getLastPlayRequestId()).isNull();
        assertThat(attempt.getLastPlayAllowed()).isNull();
    }

    @Test
    void startTask_taskScopedItem_clearsActiveSectionAndSectionStartedAt() {
        ExamItemView item = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        attempt.setActiveSection("READING");
        attempt.setSectionStartedAt(Instant.now().minusSeconds(30));

        timerService.startTask(attempt, item, List.of(item));

        assertThat(attempt.getActiveSection()).isNull();
        assertThat(attempt.getSectionStartedAt()).isNull();
    }

    @Test
    void startTask_firstReadingItem_setsActiveSectionAndStartsSectionClock() {
        List<ExamItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));

        Instant before = Instant.now();
        timerService.startTask(attempt, items.get(0), items);
        Instant after = Instant.now();

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isBetween(before, after);
    }

    @Test
    void startTask_secondReadingItem_reusesExistingSectionStartedAt_doesNotReset() {
        List<ExamItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        timerService.startTask(attempt, items.get(0), items);
        Instant firstSectionStart = attempt.getSectionStartedAt();

        timerService.startTask(attempt, items.get(1), items);

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isEqualTo(firstSectionStart);
        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(1);
    }

    @Test
    void startTask_enteringReadingAfterAnotherSection_startsFreshSectionClock() {
        ExamItemView speaking = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        ExamItemView firstReading = readingItem(1, 60);
        List<ExamItemView> items = List.of(speaking, firstReading);
        timerService.startTask(attempt, speaking, items);

        Instant before = Instant.now();
        timerService.startTask(attempt, firstReading, items);
        Instant after = Instant.now();

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isBetween(before, after);
    }

    @Test
    void resolveEffective_taskScopedItem_returnsItemsOwnValuesUnchanged() {
        ExamItemView item = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        List<ExamItemView> items = List.of(item);
        timerService.startTask(attempt, item, items);

        assertThat(timerService.resolveEffectivePrepSeconds(item)).isEqualTo(35);
        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, item, items)).isEqualTo(40);
    }

    @Test
    void resolveEffective_firstReadingItem_prepIsZero_responseIsFullSectionBudget() {
        List<ExamItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        timerService.startTask(attempt, items.get(0), items);

        assertThat(timerService.resolveEffectivePrepSeconds(items.get(0))).isZero();
        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, items.get(0), items)).isEqualTo(60 + 90 + 75);
    }

    @Test
    void resolveEffective_secondReadingItem_deductsElapsedTimeSinceSectionStart() {
        List<ExamItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        timerService.startTask(attempt, items.get(0), items);
        // Simulate 20 elapsed seconds since the section actually started, without a real sleep.
        attempt.setSectionStartedAt(attempt.getSectionStartedAt().minusSeconds(20));

        // Must reuse the SAME (already activeSection="READING") sectionStartedAt, not reset it —
        // otherwise startTask would take the "new section" branch.
        timerService.startTask(attempt, items.get(1), items);

        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, items.get(1), items);
        // 225s total budget minus ~20s elapsed — allow a small tolerance for real test execution time.
        assertThat(effectiveResponse).isBetween(203, 205);
        assertThat(timerService.resolveEffectivePrepSeconds(items.get(1))).isZero();
    }

    @Test
    void resolveEffective_elapsedExceedsBudget_clampsToZero_neverNegative() {
        List<ExamItemView> items = List.of(readingItem(0, 60));
        timerService.startTask(attempt, items.get(0), items);
        // Section budget is only 60s — simulate far more elapsed than that.
        attempt.setSectionStartedAt(attempt.getSectionStartedAt().minusSeconds(9999));

        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, items.get(0), items)).isZero();
    }

    @Test
    void multiItemReadingSection_currentPointerAndSharedBudgetStayConsistentAcrossAllThreeItems() {
        List<ExamItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));

        timerService.startTask(attempt, items.get(0), items);
        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(0);
        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        Instant sectionStart = attempt.getSectionStartedAt();

        timerService.startTask(attempt, items.get(1), items);
        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(1);
        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isEqualTo(sectionStart);

        timerService.startTask(attempt, items.get(2), items);
        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(2);
        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isEqualTo(sectionStart);

        // Every startTask call also resets the (relocated) replay counters.
        assertThat(attempt.getPlayCount()).isZero();
        assertThat(attempt.getLastPlayRequestId()).isNull();
        assertThat(attempt.getLastPlayAllowed()).isNull();
    }
}
