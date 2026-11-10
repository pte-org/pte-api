package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.AiProviderException;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The fallback of the rubric plan (phase 4) for traits whose LLM score is not stable: score the answer three times
 * and take, per selected trait, the middle of the three whole numbers. The median of three integers is always one
 * of them, so nothing is averaged or rounded. Traits that are not selected keep the first run's value.
 *
 * <p>Fails closed: it needs exactly three runs, each a complete trait set with the same keys and no missing value.
 * A failed or missing run is never replaced by the median of the other two (that would be an average), so the
 * caller must treat the answer as failed, exactly as when a single call breaks the schema.
 *
 * <p>Not wired into a scorer yet: phase 4 could not measure stability (no Gemini key), and the switch belongs to
 * the result of that measurement.
 */
public final class MedianOfThreeScoring {

    public static final int RUNS = 3;

    private static final int MIDDLE = 1;

    private MedianOfThreeScoring() {
    }

    /**
     * @param runs           three trait sets of the same answer, in call order
     * @param selectedTraits the traits that take the median; every one must be present in the runs
     * @return the combined trait set, in the first run's order
     */
    public static Map<String, Integer> combine(List<Map<String, Integer>> runs, Set<String> selectedTraits) {
        requireComplete(runs, selectedTraits);
        Map<String, Integer> first = runs.getFirst();
        Map<String, Integer> combined = new LinkedHashMap<>();
        first.forEach((trait, score) -> combined.put(trait, selectedTraits.contains(trait)
                ? median(runs.get(0).get(trait), runs.get(1).get(trait), runs.get(2).get(trait))
                : score));
        return combined;
    }

    private static int median(int first, int second, int third) {
        int[] sorted = {first, second, third};
        Arrays.sort(sorted);
        return sorted[MIDDLE];
    }

    private static void requireComplete(List<Map<String, Integer>> runs, Set<String> selectedTraits) {
        if (runs == null || runs.size() != RUNS || runs.stream().anyMatch(Objects::isNull)) {
            throw incomplete();
        }
        Set<String> keys = runs.getFirst().keySet();
        for (Map<String, Integer> run : runs) {
            if (!run.keySet().equals(keys) || run.values().stream().anyMatch(Objects::isNull)) {
                throw incomplete();
            }
        }
        if (!keys.containsAll(selectedTraits)) {
            throw incomplete();
        }
    }

    private static AiProviderException incomplete() {
        return new AiProviderException(AiFailureReason.PROVIDER_RESPONSE_INVALID,
                ScoringConstants.AI_MEDIAN_RUNS_INCOMPLETE);
    }
}
