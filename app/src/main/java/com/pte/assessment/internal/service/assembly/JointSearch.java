package com.pte.assessment.internal.service.assembly;

import java.util.List;
import java.util.Random;

/** Walks every joint choice of one combination per part, for counting candidates and for a weighted draw. */
final class JointSearch {

    private final List<PartCombos> parts;
    private long candidates;

    JointSearch(List<PartCombos> parts) {
        this.parts = parts;
    }

    long candidates() {
        return candidates;
    }

    /** Number of joint choices whose total planned time is at most {@code ceilingSeconds}. */
    long countUnder(long ceilingSeconds) {
        scan(ceilingSeconds, null, 0, true);
        return candidates;
    }

    /** One joint choice drawn by weight among those under the ceiling; extremes are skipped when others exist. */
    int[] pick(Random random, long ceilingSeconds) {
        boolean avoidExtremes = true;
        double totalWeight = scan(ceilingSeconds, null, 0, avoidExtremes);
        if (totalWeight <= 0) {
            avoidExtremes = false;
            totalWeight = scan(ceilingSeconds, null, 0, false);
        }
        int[] chosen = new int[parts.size()];
        scan(ceilingSeconds, chosen, random.nextDouble() * totalWeight, avoidExtremes);
        return chosen;
    }

    // With chosen == null this only sums weights and counts candidates; otherwise it stops at the draw target.
    private double scan(long ceilingSeconds, int[] chosen, double target, boolean avoidExtremes) {
        int[] index = new int[parts.size()];
        int[] lastPositive = null;
        double accumulated = 0;
        long counted = 0;
        boolean more = true;
        while (more) {
            long total = 0;
            double weight = 1;
            boolean allMinimum = true;
            boolean allMaximum = true;
            for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
                PartCombos part = parts.get(partIndex);
                total += part.totalOf(index[partIndex]);
                weight *= part.weightOf(index[partIndex], BalancedCountPlanner.CLOSENESS_TO_MIDDLE);
                allMinimum &= part.isAllMinimum(index[partIndex]);
                allMaximum &= part.isAllMaximum(index[partIndex]);
            }
            if (total <= ceilingSeconds) {
                counted++;
                if (avoidExtremes && (allMinimum || allMaximum)) {
                    weight = 0;
                }
                accumulated += weight;
                if (weight > 0) {
                    lastPositive = index.clone();
                    if (chosen != null && accumulated >= target) {
                        System.arraycopy(index, 0, chosen, 0, index.length);
                        return accumulated;
                    }
                }
            }
            more = advance(index);
        }
        candidates = counted;
        if (chosen != null && lastPositive != null) {
            System.arraycopy(lastPositive, 0, chosen, 0, lastPositive.length);
        }
        return accumulated;
    }

    // Odometer step over the parts; false once every combination has been visited.
    private boolean advance(int[] index) {
        for (int partIndex = parts.size() - 1; partIndex >= 0; partIndex--) {
            if (++index[partIndex] < parts.get(partIndex).size()) {
                return true;
            }
            index[partIndex] = 0;
        }
        return false;
    }
}
