package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the section-scoped vs task-scoped budget split (READING shares one
 * budget across every task in the section, summed from all its items;
 * everything else keeps the original per-task values) — client-side-exam-timer
 * Phase 5 rewrite: no deadlines, no {@code TimerState}, no repository at all.
 */
class TimerServiceTest {

    private final TimerService timerService = new TimerService();
    private ExamAttempt attempt;

    @BeforeEach
    void setUp() {
        attempt = new ExamAttempt();
    }

    private PinnedItemView readingItem(int orderIndex, int responseSeconds) {
        return new PinnedItemView(UUID.randomUUID(), orderIndex, "READING", "MC_READING_SINGLE", "title", "prompt",
                null, null, null, null, null, null, "[]", 0, responseSeconds, null, null, null);
    }

    private PinnedItemView taskScopedItem(int orderIndex, String section, String taskType, int prepSeconds,
                                           int responseSeconds) {
        return new PinnedItemView(UUID.randomUUID(), orderIndex, section, taskType, "title", "prompt", null, null,
                null, null, null, null, "[]", prepSeconds, responseSeconds, null, null, null);
    }

    @Test
    void startTask_setsCurrentOrderIndex_andResetsReplayCounters() {
        PinnedItemView item = taskScopedItem(2, "SPEAKING", "READ_ALOUD", 35, 40);
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
        PinnedItemView item = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        attempt.setActiveSection("READING");
        attempt.setSectionStartedAt(Instant.now().minusSeconds(30));

        timerService.startTask(attempt, item, List.of(item));

        assertThat(attempt.getActiveSection()).isNull();
        assertThat(attempt.getSectionStartedAt()).isNull();
    }

    @Test
    void startTask_firstReadingItem_setsActiveSectionAndStartsSectionClock() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));

        Instant before = Instant.now();
        timerService.startTask(attempt, items.get(0), items);
        Instant after = Instant.now();

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isBetween(before, after);
    }

    @Test
    void startTask_secondReadingItem_reusesExistingSectionStartedAt_doesNotReset() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        timerService.startTask(attempt, items.get(0), items);
        Instant firstSectionStart = attempt.getSectionStartedAt();

        timerService.startTask(attempt, items.get(1), items);

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isEqualTo(firstSectionStart);
        assertThat(attempt.getCurrentOrderIndex()).isEqualTo(1);
    }

    @Test
    void startTask_enteringReadingAfterAnotherSection_startsFreshSectionClock() {
        PinnedItemView speaking = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        PinnedItemView firstReading = readingItem(1, 60);
        List<PinnedItemView> items = List.of(speaking, firstReading);
        timerService.startTask(attempt, speaking, items);

        Instant before = Instant.now();
        timerService.startTask(attempt, firstReading, items);
        Instant after = Instant.now();

        assertThat(attempt.getActiveSection()).isEqualTo("READING");
        assertThat(attempt.getSectionStartedAt()).isBetween(before, after);
    }

    @Test
    void resolveEffective_taskScopedItem_returnsItemsOwnValuesUnchanged() {
        PinnedItemView item = taskScopedItem(0, "SPEAKING", "READ_ALOUD", 35, 40);
        List<PinnedItemView> items = List.of(item);
        timerService.startTask(attempt, item, items);

        assertThat(timerService.resolveEffectivePrepSeconds(item)).isEqualTo(35);
        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, item, items)).isEqualTo(40);
    }

    @Test
    void resolveEffective_firstReadingItem_prepIsZero_responseIsFullSectionBudget() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
        timerService.startTask(attempt, items.get(0), items);

        assertThat(timerService.resolveEffectivePrepSeconds(items.get(0))).isZero();
        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, items.get(0), items)).isEqualTo(60 + 90 + 75);
    }

    @Test
    void resolveEffective_secondReadingItem_deductsElapsedTimeSinceSectionStart() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));
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
        List<PinnedItemView> items = List.of(readingItem(0, 60));
        timerService.startTask(attempt, items.get(0), items);
        // Section budget is only 60s — simulate far more elapsed than that.
        attempt.setSectionStartedAt(attempt.getSectionStartedAt().minusSeconds(9999));

        assertThat(timerService.resolveEffectiveResponseSeconds(attempt, items.get(0), items)).isZero();
    }

    /**
     * Regression test (client-side-exam-timer Phase 1, plan Step 7 — preserved
     * through the Phase 5 rewrite): confirms the pointer (currentOrderIndex) and
     * the shared-budget clock (sectionStartedAt) stay consistent across all
     * three items of a Reading section.
     */
    @Test
    void multiItemReadingSection_currentPointerAndSharedBudgetStayConsistentAcrossAllThreeItems() {
        List<PinnedItemView> items = List.of(readingItem(0, 60), readingItem(1, 90), readingItem(2, 75));

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
