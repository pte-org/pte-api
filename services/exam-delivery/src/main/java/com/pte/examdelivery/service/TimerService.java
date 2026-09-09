package com.pte.examdelivery.service;

import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.service.cache.PinnedItemView;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Client-side-exam-timer Phase 5: server-side deadline enforcement (prep/response
 * windows, the response-window-expiry check, the {@code /timer} poll endpoint) is
 * gone entirely — the client now owns the whole-attempt countdown and auto-submit
 * UX (see {@code research/refactor_polling.md}). What remains here has no other
 * home: resetting per-task replay counters when a new task starts, and tracking
 * the section-scoped shared-budget window (currently only READING) so
 * {@link #resolveEffectiveResponseSeconds} can hand the client a correct live
 * value on every per-task fetch — no absolute deadline, no polling required.
 *
 * <p>Sections in {@link #SECTION_SCOPED_SECTIONS} share a single budget across
 * every task in the section instead of each task getting its own — computed once,
 * when the first task of the section starts, as the sum of every task's own
 * {@code prepSeconds + responseSeconds} in that section's contiguous run. All
 * other sections (SPEAKING, WRITING, LISTENING) keep the original per-task values.
 */
@Service
public class TimerService {

    private static final Set<String> SECTION_SCOPED_SECTIONS = Set.of("READING");

    /**
     * Resets the per-task bookkeeping {@code startTask} owns (replay counters,
     * section-scoped tracking) — called whenever {@code attempt}'s current task
     * changes, so this must run BEFORE {@link #resolveEffectivePrepSeconds}/
     * {@link #resolveEffectiveResponseSeconds} are read for {@code item}.
     *
     * @param allItems every item of the attempt's pinned snapshot, in
     *                  {@code orderIndex} order — only scanned when {@code item}
     *                  is the first task of a new section-scoped section, to sum
     *                  that section's total budget (via
     *                  {@link #resolveEffectiveResponseSeconds}, not here).
     */
    public void startTask(ExamAttempt attempt, PinnedItemView item, List<PinnedItemView> allItems) {
        attempt.setCurrentOrderIndex(item.orderIndex());
        attempt.setPlayCount(0);
        attempt.setLastPlayRequestId(null);
        attempt.setLastPlayAllowed(null);

        if (SECTION_SCOPED_SECTIONS.contains(item.section())) {
            if (!item.section().equals(attempt.getActiveSection())) {
                // Just entered this section-scoped section — the budget's clock
                // starts now. Reuse the existing sectionStartedAt when merely
                // moving between items already inside the same section.
                attempt.setSectionStartedAt(Instant.now());
            }
            attempt.setActiveSection(item.section());
        } else {
            attempt.setActiveSection(null);
            attempt.setSectionStartedAt(null);
        }
    }

    /**
     * The prep/response seconds the CLIENT should actually count down from for
     * {@code item} (client-side-exam-timer Phase 2 addendum, FR-01/FR-06). For a
     * task-scoped item this is just the item's own static values, unchanged. For a
     * section-scoped item (READING), the item's own {@code prepSeconds}/{@code responseSeconds}
     * are just that item's individual slice — the value the client needs is the
     * *live remaining shared budget*, computed fresh here from
     * {@link ExamAttempt#getSectionStartedAt()}. Must be called AFTER
     * {@link #startTask}, which is what sets/maintains that attempt field.
     */
    public int resolveEffectivePrepSeconds(PinnedItemView item) {
        return SECTION_SCOPED_SECTIONS.contains(item.section()) ? 0 : item.prepSeconds();
    }

    public int resolveEffectiveResponseSeconds(ExamAttempt attempt, PinnedItemView item, List<PinnedItemView> allItems) {
        if (!SECTION_SCOPED_SECTIONS.contains(item.section())) {
            return item.responseSeconds();
        }
        long elapsedSeconds = Duration.between(attempt.getSectionStartedAt(), Instant.now()).getSeconds();
        return (int) Math.max(0, sectionBudgetSeconds(item, allItems) - elapsedSeconds);
    }

    /**
     * Sums {@code prepSeconds + responseSeconds} across the contiguous run of
     * {@code allItems} sharing {@code item.section()} that contains
     * {@code item.orderIndex()} — items are expected to be grouped by section
     * (matching real PTE section ordering), so a single forward/backward scan
     * from {@code item}'s own position finds the whole run.
     */
    private long sectionBudgetSeconds(PinnedItemView item, List<PinnedItemView> allItems) {
        return allItems.stream()
                .filter(candidate -> item.section().equals(candidate.section()))
                .mapToLong(candidate -> (long) candidate.prepSeconds() + candidate.responseSeconds())
                .sum();
    }
}
