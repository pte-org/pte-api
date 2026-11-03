package com.pte.assessment.internal.service.assembly;

import com.pte.assessment.dto.response.ExamTimeFrameResponse;
import com.pte.assessment.internal.service.timing.ExamTimePart;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Chooses how many questions each task type gets so that every part's planned duration lands inside its window
 * and the exam is balanced for total length: among the combinations that fit, one is drawn at random, favouring
 * combinations near the middle of each window, and the all-minimum and all-maximum extremes are avoided while
 * any other combination exists. The same seed always gives the same answer.
 *
 * <p>The total ceiling is a soft limit. Answer times listed as "up to N minutes" are stored at their upper
 * bound, so a plan can exceed the ceiling on paper while real time stays under it; when no combination inside
 * every window stays under the ceiling, the plan ignores it and says so in {@code totalCeilingMet}.
 */
public final class BalancedCountPlanner {

    /** Sharpness of the preference for the middle of a window; larger values hug the middle more. */
    static final double CLOSENESS_TO_MIDDLE = 2.0;
    /** Largest number of combinations examined across the three parts; beyond it the plan is not attempted. */
    static final long MAX_COMBINATIONS = 5_000_000L;

    private BalancedCountPlanner() {
    }

    public static PlanResult plan(List<SlotRequirement> slots, ExamTimeFrameResponse frame, long seed) {
        Map<ExamTimePart, PartCombos> parts = new EnumMap<>(ExamTimePart.class);
        List<PlanResult.PartFailure> failures = new ArrayList<>();
        for (ExamTimePart part : ExamTimePart.values()) {
            List<SlotRequirement> partSlots = slots.stream().filter(slot -> slot.part() == part).toList();
            if (partSlots.isEmpty()) {
                continue;
            }
            long frameMin = frameMin(part, frame);
            long frameMax = frameMax(part, frame);
            PartCombos combos = PartCombos.enumerate(partSlots, frameMin, frameMax);
            if (combos.isEmpty()) {
                failures.add(new PlanResult.PartFailure(part, minReachable(partSlots), maxReachable(partSlots),
                        frameMin, frameMax));
            }
            parts.put(part, combos);
        }
        if (!failures.isEmpty()) {
            return PlanResult.infeasible(failures);
        }
        return choose(parts, frame, seed);
    }

    private static PlanResult choose(Map<ExamTimePart, PartCombos> parts, ExamTimeFrameResponse frame, long seed) {
        List<PartCombos> ordered = new ArrayList<>(parts.values());
        long product = 1;
        for (PartCombos combos : ordered) {
            product *= combos.size();
            if (product > MAX_COMBINATIONS) {
                return PlanResult.infeasible(List.of());
            }
        }
        JointSearch search = new JointSearch(ordered);
        boolean underCeiling = search.countUnder(frame.totalMaxSeconds()) > 0;
        long ceiling = underCeiling ? frame.totalMaxSeconds() : Long.MAX_VALUE;
        int[] chosen = search.pick(Seeds.random(seed), ceiling);
        Map<String, Integer> counts = new HashMap<>();
        Map<ExamTimePart, Long> partSeconds = new EnumMap<>(ExamTimePart.class);
        long total = 0;
        for (int index = 0; index < ordered.size(); index++) {
            PartCombos combos = ordered.get(index);
            counts.putAll(combos.countsOf(chosen[index]));
            partSeconds.put(combos.part(), combos.totalOf(chosen[index]));
            total += combos.totalOf(chosen[index]);
        }
        return new PlanResult(Map.copyOf(counts), Map.copyOf(partSeconds), total, search.candidates(), underCeiling,
                List.of());
    }

    private static long minReachable(List<SlotRequirement> slots) {
        return slots.stream().mapToLong(slot -> slot.minCount() * slot.secondsPerQuestion()).sum();
    }

    private static long maxReachable(List<SlotRequirement> slots) {
        return slots.stream().mapToLong(slot -> slot.maxCount() * slot.secondsPerQuestion()).sum();
    }

    static long frameMin(ExamTimePart part, ExamTimeFrameResponse frame) {
        return switch (part) {
            case SPEAKING_WRITING -> frame.speakingWritingMinSeconds();
            case READING -> frame.readingMinSeconds();
            case LISTENING -> frame.listeningMinSeconds();
        };
    }

    static long frameMax(ExamTimePart part, ExamTimeFrameResponse frame) {
        return switch (part) {
            case SPEAKING_WRITING -> frame.speakingWritingMaxSeconds();
            case READING -> frame.readingMaxSeconds();
            case LISTENING -> frame.listeningMaxSeconds();
        };
    }
}
