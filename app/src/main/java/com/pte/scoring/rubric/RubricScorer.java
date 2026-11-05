package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * The only function that turns trait scores into an item score. Order: validate every trait (present, known,
 * integer within its scale; never clamped), apply the gates (Score Guide p.8: Content or Form 0 means no points for
 * the response), then {@code Σ(weight × achieved / max) / Σ weight × 100}, rounded half-up. With the default
 * weights (each trait weighs its maximum) this equals Σ achieved / Σ maximum.
 */
public final class RubricScorer {

    private static final MathContext MATH = MathContext.DECIMAL128;

    private RubricScorer() {
    }

    public static ScoreTrace score(EffectiveRubric rubric, TraitScoreSet scores) {
        TaskRubric task = rubric.task();
        if (task.isObjective()) {
            throw new RubricScoreException(RubricConstants.RUBRIC_TASK_TYPE_NOT_TRAIT_SCORED);
        }
        rejectUnknownTraits(task, scores);
        List<TraitContribution> contributions = new ArrayList<>();
        String gate = null;
        for (TraitDefinition trait : task.traits()) {
            TraitContribution contribution = contributionOf(rubric, trait, scores);
            contributions.add(contribution);
            if (gate == null && contribution.achieved() == 0 && rubric.isGateEnabled(trait.key())) {
                gate = trait.key();
            }
        }
        if (gate != null) {
            return new ScoreTrace(0, BigDecimal.ZERO, gate, contributions);
        }
        Fraction fraction = weightedFraction(contributions);
        return new ScoreTrace(fraction.roundedHalfUp(), fraction.toPercent(), null, contributions);
    }

    private static void rejectUnknownTraits(TaskRubric task, TraitScoreSet scores) {
        for (String key : scores.scores().keySet()) {
            if (task.trait(key).isEmpty()) {
                throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_UNKNOWN);
            }
        }
    }

    private static TraitContribution contributionOf(EffectiveRubric rubric, TraitDefinition trait,
                                                    TraitScoreSet scores) {
        Integer achieved = scores.scores().get(trait.key());
        if (achieved == null) {
            throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_MISSING);
        }
        int max = rubric.maxOf(trait, scores.wordCount());
        if (achieved < 0 || achieved > max) {
            throw new RubricScoreException(RubricConstants.RUBRIC_TRAIT_OUT_OF_RANGE);
        }
        BigDecimal weight = BigDecimal.valueOf(rubric.weightOf(trait.key()));
        BigDecimal effectiveWeight = trait.scale().isPerWord() ? weight.multiply(BigDecimal.valueOf(max)) : weight;
        BigDecimal weightedContribution = effectiveWeight.multiply(BigDecimal.valueOf(achieved))
                .divide(BigDecimal.valueOf(max), MATH);
        return new TraitContribution(trait.key(), achieved, max, effectiveWeight, weightedContribution);
    }

    /**
     * Exact rational arithmetic: Σ(weight × achieved / max) is accumulated as one fraction of BigIntegers so that an
     * item score landing exactly on x.5 is never pushed below it by an earlier rounded division.
     */
    private static Fraction weightedFraction(List<TraitContribution> contributions) {
        BigInteger numerator = BigInteger.ZERO;
        BigInteger denominator = BigInteger.ONE;
        BigInteger totalWeight = BigInteger.ZERO;
        for (TraitContribution contribution : contributions) {
            BigInteger weight = contribution.effectiveWeight().toBigIntegerExact();
            BigInteger max = BigInteger.valueOf(contribution.max());
            numerator = numerator.multiply(max)
                    .add(weight.multiply(BigInteger.valueOf(contribution.achieved())).multiply(denominator));
            denominator = denominator.multiply(max);
            totalWeight = totalWeight.add(weight);
        }
        return new Fraction(numerator.multiply(BigInteger.valueOf(100)), denominator.multiply(totalWeight));
    }

    private record Fraction(BigInteger numerator, BigInteger denominator) {

        BigDecimal toPercent() {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), MATH);
        }

        int roundedHalfUp() {
            return new BigDecimal(numerator).divide(new BigDecimal(denominator), 0, RoundingMode.HALF_UP)
                    .intValueExact();
        }
    }
}
