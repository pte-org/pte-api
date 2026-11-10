package com.pte.scoring.internal.rubric.stability;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Pure arithmetic of the phase 4 stability spike (plan quang-scoring-rubric-transparency). Each sample is scored
 * {@link #RUNS} times; a trait of a sample is <em>stable</em> when all runs produced it and the spread (max - min)
 * is at most {@link #MAX_SPREAD} level. The gate metric is stable / total over every (sample, trait) pair, where a
 * failed or missing run makes the pair unstable (a failure is not a stable score). "Within one of the intended
 * band" is reported per run score but never gates. Holds counts only; no answer text ever enters it.
 */
public final class StabilityMetrics {

    public static final int RUNS = 3;
    public static final int MAX_SPREAD = 1;
    public static final double GATE_THRESHOLD = 0.90;
    public static final String MISSING_RUN = "MISSING_RUN";

    private static final String KEY_SEPARATOR = ":";

    private StabilityMetrics() {
    }

    /**
     * One scorer call.
     *
     * @param traits         the trait scores (null when the call failed)
     * @param failureReason  the failure code (null on success)
     * @param schemaAttempts model calls the scorer needed (1 = no retry)
     */
    public record RunOutcome(Map<String, Integer> traits, String failureReason, int schemaAttempts, long latencyMs) {

        public static RunOutcome success(Map<String, Integer> traits, int schemaAttempts, long latencyMs) {
            return new RunOutcome(Map.copyOf(traits), null, schemaAttempts, latencyMs);
        }

        public static RunOutcome failure(String reason, long latencyMs) {
            return new RunOutcome(null, reason, 0, latencyMs);
        }

        public boolean failed() {
            return traits == null;
        }
    }

    /** The runs of one sample; {@code traitKeys} are the traits measured (the model's traits, not coded Form). */
    public record SampleRuns(String sampleId, String taskType, List<String> traitKeys,
                             Map<String, Integer> intendedBands, List<RunOutcome> runs) {
    }

    public record TraitStat(int total, int complete, int stable, int intendedChecked, int intendedWithinOne) {

        static final TraitStat EMPTY = new TraitStat(0, 0, 0, 0, 0);

        TraitStat plus(TraitStat other) {
            return new TraitStat(total + other.total, complete + other.complete, stable + other.stable,
                    intendedChecked + other.intendedChecked, intendedWithinOne + other.intendedWithinOne);
        }

        public double stableRate() {
            return rate(stable, total);
        }

        public double stableRateAmongComplete() {
            return rate(stable, complete);
        }

        public double intendedWithinOneRate() {
            return rate(intendedWithinOne, intendedChecked);
        }

        Map<String, Object> toAggregates() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", total);
            out.put("complete", complete);
            out.put("stable", stable);
            out.put("stableRate", stableRate());
            out.put("stableRateAmongComplete", stableRateAmongComplete());
            out.put("intendedChecked", intendedChecked);
            out.put("intendedWithinOne", intendedWithinOne);
            out.put("intendedWithinOneRate", intendedWithinOneRate());
            return out;
        }
    }

    /** {@code byTrait} is keyed {@code TASK_TYPE:TRAIT}, because a trait's scale differs between task types. */
    public record StabilityReport(Map<String, TraitStat> byTrait, TraitStat overall, int runs, int failedRuns,
                                  Map<String, Integer> failureReasons, int runsWithRetry, int extraAttempts,
                                  long latencyAverageMs, long latencyMaxMs) {

        public double failureRate() {
            return rate(failedRuns, runs);
        }

        public boolean meetsGate() {
            return overall.total() > 0 && overall.stableRate() >= GATE_THRESHOLD;
        }

        public Map<String, Object> toAggregates() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("overall", overall.toAggregates());
            Map<String, Object> traits = new LinkedHashMap<>();
            byTrait.forEach((key, stat) -> traits.put(key, stat.toAggregates()));
            out.put("byTrait", traits);
            out.put("runs", runs);
            out.put("failedRuns", failedRuns);
            out.put("failureRate", failureRate());
            out.put("failureReasons", failureReasons);
            out.put("runsWithRetry", runsWithRetry);
            out.put("extraAttempts", extraAttempts);
            out.put("latencyAverageMs", latencyAverageMs);
            out.put("latencyMaxMs", latencyMaxMs);
            out.put("gateThreshold", GATE_THRESHOLD);
            out.put("maxSpread", MAX_SPREAD);
            out.put("meetsGate", meetsGate());
            return out;
        }
    }

    public static StabilityReport compute(List<SampleRuns> samples) {
        Map<String, TraitStat> byTrait = new TreeMap<>();
        Map<String, Integer> reasons = new TreeMap<>();
        int runs = 0;
        int failed = 0;
        int withRetry = 0;
        int extra = 0;
        long latencyTotal = 0;
        long latencyMax = 0;
        int recorded = 0;
        for (SampleRuns sample : samples) {
            if (sample.runs().size() > RUNS) {
                throw new IllegalArgumentException("A sample has more than " + RUNS + " runs");
            }
            int missing = RUNS - sample.runs().size();
            runs += RUNS;
            failed += missing;
            if (missing > 0) {
                reasons.merge(MISSING_RUN, missing, Integer::sum);
            }
            for (RunOutcome run : sample.runs()) {
                recorded++;
                latencyTotal += run.latencyMs();
                latencyMax = Math.max(latencyMax, run.latencyMs());
                if (run.failed()) {
                    failed++;
                    reasons.merge(run.failureReason(), 1, Integer::sum);
                } else if (run.schemaAttempts() > 1) {
                    withRetry++;
                    extra += run.schemaAttempts() - 1;
                }
            }
            for (String trait : sample.traitKeys()) {
                byTrait.merge(sample.taskType() + KEY_SEPARATOR + trait, traitStat(sample, trait), TraitStat::plus);
            }
        }
        TraitStat overall = byTrait.values().stream().reduce(TraitStat.EMPTY, TraitStat::plus);
        long latencyAverage = recorded == 0 ? 0 : latencyTotal / recorded;
        return new StabilityReport(Collections.unmodifiableMap(byTrait), overall, runs, failed,
                Collections.unmodifiableMap(reasons), withRetry, extra, latencyAverage, latencyMax);
    }

    private static TraitStat traitStat(SampleRuns sample, String trait) {
        Integer intended = sample.intendedBands().get(trait);
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        int scored = 0;
        int checked = 0;
        int withinOne = 0;
        for (RunOutcome run : sample.runs()) {
            Integer score = run.failed() ? null : run.traits().get(trait);
            if (score == null) {
                continue;
            }
            scored++;
            min = Math.min(min, score);
            max = Math.max(max, score);
            if (intended != null) {
                checked++;
                withinOne += Math.abs(score - intended) <= 1 ? 1 : 0;
            }
        }
        boolean complete = scored == RUNS;
        boolean stable = complete && max - min <= MAX_SPREAD;
        return new TraitStat(1, complete ? 1 : 0, stable ? 1 : 0, checked, withinOne);
    }

    private static double rate(int part, int whole) {
        return whole == 0 ? 0.0 : (double) part / whole;
    }
}
