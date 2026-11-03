package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.internal.service.timing.ExamTimePart;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every combination of question counts of one part whose planned duration lies inside the part's window. */
final class PartCombos {

    private final ExamTimePart part;
    private final List<SlotRequirement> slots;
    private final long frameMinSeconds;
    private final long frameMaxSeconds;
    private final List<int[]> counts = new ArrayList<>();
    private final List<Long> totals = new ArrayList<>();
    private final Map<Long, Integer> combosWithSameTotal = new HashMap<>();

    private PartCombos(ExamTimePart part, List<SlotRequirement> slots, long frameMinSeconds, long frameMaxSeconds) {
        this.part = part;
        this.slots = slots;
        this.frameMinSeconds = frameMinSeconds;
        this.frameMaxSeconds = frameMaxSeconds;
    }

    static PartCombos enumerate(List<SlotRequirement> slots, long frameMinSeconds, long frameMaxSeconds) {
        PartCombos combos = new PartCombos(slots.get(0).part(), slots, frameMinSeconds, frameMaxSeconds);
        long[] suffixMin = new long[slots.size() + 1];
        long[] suffixMax = new long[slots.size() + 1];
        for (int index = slots.size() - 1; index >= 0; index--) {
            SlotRequirement slot = slots.get(index);
            suffixMin[index] = suffixMin[index + 1] + slot.minCount() * slot.secondsPerQuestion();
            suffixMax[index] = suffixMax[index + 1] + slot.maxCount() * slot.secondsPerQuestion();
        }
        combos.extend(0, 0, new int[slots.size()], suffixMin, suffixMax);
        return combos;
    }

    // Depth-first over the slots; a branch is dropped as soon as no completion can reach the window.
    private void extend(int index, long secondsSoFar, int[] chosen, long[] suffixMin, long[] suffixMax) {
        if (secondsSoFar + suffixMin[index] > frameMaxSeconds || secondsSoFar + suffixMax[index] < frameMinSeconds) {
            return;
        }
        if (index == slots.size()) {
            counts.add(chosen.clone());
            totals.add(secondsSoFar);
            combosWithSameTotal.merge(secondsSoFar, 1, Integer::sum);
            return;
        }
        SlotRequirement slot = slots.get(index);
        for (int count = slot.minCount(); count <= slot.maxCount(); count++) {
            chosen[index] = count;
            extend(index + 1, secondsSoFar + count * slot.secondsPerQuestion(), chosen, suffixMin, suffixMax);
        }
    }

    ExamTimePart part() {
        return part;
    }

    boolean isEmpty() {
        return counts.isEmpty();
    }

    int size() {
        return counts.size();
    }

    long totalOf(int combo) {
        return totals.get(combo);
    }

    Map<String, Integer> countsOf(int combo) {
        Map<String, Integer> byTaskType = new HashMap<>();
        int[] chosen = counts.get(combo);
        for (int index = 0; index < slots.size(); index++) {
            byTaskType.put(slots.get(index).taskTypeKey(), chosen[index]);
        }
        return byTaskType;
    }

    boolean isAllMinimum(int combo) {
        int[] chosen = counts.get(combo);
        for (int index = 0; index < slots.size(); index++) {
            if (chosen[index] != slots.get(index).minCount()) {
                return false;
            }
        }
        return true;
    }

    boolean isAllMaximum(int combo) {
        int[] chosen = counts.get(combo);
        for (int index = 0; index < slots.size(); index++) {
            if (chosen[index] != slots.get(index).maxCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Weight in (0, 1]: 1 for a combination in the middle of the window, falling off towards its edges. It is
     * divided by how many combinations take the same total time, so every distinct duration is equally likely
     * to be drawn however many ways it can be reached; without that, the many combinations at the short end
     * of the window would crowd out the few near its middle.
     */
    double weightOf(int combo, double closenessToMiddle) {
        double middle = (frameMinSeconds + frameMaxSeconds) / 2.0;
        double halfWidth = Math.max((frameMaxSeconds - frameMinSeconds) / 2.0, 1.0);
        double distance = (totals.get(combo) - middle) / halfWidth;
        return Math.exp(-closenessToMiddle * distance * distance) / combosWithSameTotal.get(totals.get(combo));
    }
}
