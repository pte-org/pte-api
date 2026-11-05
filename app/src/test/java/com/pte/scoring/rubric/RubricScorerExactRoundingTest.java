package com.pte.scoring.rubric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;

import static com.pte.scoring.rubric.RubricTestSupport.effective;
import static com.pte.scoring.rubric.RubricTestSupport.scores;
import static com.pte.scoring.rubric.RubricTestSupport.set;
import static com.pte.scoring.rubric.RubricTestSupport.withWeights;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Item scores landing exactly on x.5 must round up; the expected value is computed independently with BigInteger. */
class RubricScorerExactRoundingTest {

    private static final BigDecimal TOLERANCE = new BigDecimal("1e-10");
    private static final MathContext PRECISE = new MathContext(60);

    /** Exact HALF_UP of 100 * sum(w_i * a_i / m_i) / sum(w_i) using only integer arithmetic. */
    private static int exactHalfUp(long[] weights, long[] achieved, long[] maxes) {
        BigInteger common = BigInteger.ONE;
        for (long max : maxes) {
            common = common.multiply(BigInteger.valueOf(max)); // a common multiple of every max
        }
        BigInteger sum = BigInteger.ZERO;
        BigInteger totalWeight = BigInteger.ZERO;
        for (int i = 0; i < weights.length; i++) {
            sum = sum.add(BigInteger.valueOf(weights[i] * achieved[i])
                    .multiply(common.divide(BigInteger.valueOf(maxes[i]))));
            totalWeight = totalWeight.add(BigInteger.valueOf(weights[i]));
        }
        BigInteger num = sum.multiply(BigInteger.valueOf(100));
        BigInteger den = common.multiply(totalWeight);
        // floor((2 * num + den) / (2 * den)) is HALF_UP for non-negative values
        return num.shiftLeft(1).add(den).divide(den.shiftLeft(1)).intValueExact();
    }

    private static BigDecimal exactPercent(long[] weights, long[] achieved, long[] maxes) {
        BigInteger common = BigInteger.ONE;
        for (long max : maxes) {
            common = common.multiply(BigInteger.valueOf(max));
        }
        BigInteger sum = BigInteger.ZERO;
        BigInteger totalWeight = BigInteger.ZERO;
        for (int i = 0; i < weights.length; i++) {
            sum = sum.add(BigInteger.valueOf(weights[i] * achieved[i])
                    .multiply(common.divide(BigInteger.valueOf(maxes[i]))));
            totalWeight = totalWeight.add(BigInteger.valueOf(weights[i]));
        }
        return new BigDecimal(sum.multiply(BigInteger.valueOf(100)))
                .divide(new BigDecimal(common.multiply(totalWeight)), PRECISE);
    }

    @Test
    void readAloudOf30Words_landingExactlyOn62Point5_roundsUpTo63() {
        // N = 30: weights 30 / 5 / 5, maxima 30 / 5 / 5. Content 15, Pron 5, Fluency 5:
        // (15 + 5 + 5) / 40 * 100 = 62.5 exactly.
        TraitScoreSet input = new TraitScoreSet(scores(RubricTraits.CONTENT, 15, RubricTraits.PRONUNCIATION, 5,
                RubricTraits.ORAL_FLUENCY, 5), 30);

        ScoreTrace trace = RubricScorer.score(effective("READ_ALOUD"), input);

        long[] weights = {30, 5, 5};
        long[] achieved = {15, 5, 5};
        long[] maxes = {30, 5, 5};
        assertThat(exactPercent(weights, achieved, maxes)).isEqualByComparingTo("62.5");
        assertThat(trace.itemScore()).isEqualTo(63).isEqualTo(exactHalfUp(weights, achieved, maxes));
        assertThat(trace.rawPercent()).isCloseTo(new BigDecimal("62.5"), within(TOLERANCE));
        assertThat(trace.gateTriggeredBy()).isNull();
    }

    @Test
    void describeImageWithThirdsAndFifthsShares_landingOnXPointFive_roundsUp() {
        // Weights Content 3 / Pron 3 / Fluency 2 (sum 8), maxima 6 / 5 / 5.
        // Content 2, Pron 2, Fluency 0:
        //   percent = 100 * (3*2/6 + 3*2/5 + 2*0/5) / 8 = 100 * (1 + 6/5) / 8 = 100 * 2.2 / 8 = 27.5
        // The /6 and /5 shares have no finite binary expansion; the exact sum is 27.5 -> 28.
        EffectiveRubric rubric = withWeights("DESCRIBE_IMAGE", Map.of(RubricTraits.CONTENT, 3,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 2));

        ScoreTrace trace = RubricScorer.score(rubric, set(scores(RubricTraits.CONTENT, 2,
                RubricTraits.PRONUNCIATION, 2, RubricTraits.ORAL_FLUENCY, 0)));

        long[] weights = {3, 3, 2};
        long[] achieved = {2, 2, 0};
        long[] maxes = {6, 5, 5};
        assertThat(exactPercent(weights, achieved, maxes)).isEqualByComparingTo("27.5");
        assertThat(trace.itemScore()).isEqualTo(28).isEqualTo(exactHalfUp(weights, achieved, maxes));
        assertThat(trace.rawPercent()).isCloseTo(new BigDecimal("27.5"), within(TOLERANCE));
    }

    @Test
    void describeImageWithAnotherMix_landingOnXPointFive_roundsUp() {
        // Same weights; Content 4, Pron 3, Fluency 1:
        //   100 * (3*4/6 + 3*3/5 + 2*1/5) / 8 = 100 * (2 + 1.8 + 0.4) / 8 = 100 * 4.2 / 8 = 52.5 -> 53
        EffectiveRubric rubric = withWeights("DESCRIBE_IMAGE", Map.of(RubricTraits.CONTENT, 3,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 2));

        ScoreTrace trace = RubricScorer.score(rubric, set(scores(RubricTraits.CONTENT, 4,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 1)));

        long[] weights = {3, 3, 2};
        long[] achieved = {4, 3, 1};
        long[] maxes = {6, 5, 5};
        assertThat(exactPercent(weights, achieved, maxes)).isEqualByComparingTo("52.5");
        assertThat(trace.itemScore()).isEqualTo(53).isEqualTo(exactHalfUp(weights, achieved, maxes));
        assertThat(trace.rawPercent()).isCloseTo(new BigDecimal("52.5"), within(TOLERANCE));
    }

    @Test
    void everyDescribeImageScoreUnderMixedWeights_matchesTheIndependentExactValue() {
        long[] weights = {3, 3, 2};
        long[] maxes = {6, 5, 5};
        EffectiveRubric rubric = withWeights("DESCRIBE_IMAGE", Map.of(RubricTraits.CONTENT, 3,
                RubricTraits.PRONUNCIATION, 3, RubricTraits.ORAL_FLUENCY, 2));
        int exactHalves = 0;

        for (int content = 1; content <= 6; content++) { // 0 would trigger the Content gate
            for (int pron = 0; pron <= 5; pron++) {
                for (int fluency = 0; fluency <= 5; fluency++) {
                    long[] achieved = {content, pron, fluency};
                    ScoreTrace trace = RubricScorer.score(rubric, set(scores(RubricTraits.CONTENT, content,
                            RubricTraits.PRONUNCIATION, pron, RubricTraits.ORAL_FLUENCY, fluency)));

                    BigDecimal exact = exactPercent(weights, achieved, maxes);
                    assertThat(trace.itemScore()).isEqualTo(exactHalfUp(weights, achieved, maxes));
                    assertThat(trace.rawPercent()).isCloseTo(exact, within(TOLERANCE));
                    if (exact.remainder(BigDecimal.ONE).compareTo(new BigDecimal("0.5")) == 0) {
                        exactHalves++;
                    }
                }
            }
        }
        assertThat(exactHalves).isPositive();
    }

    @Test
    void everyReadAloudScoreOf30Words_matchesTheIndependentExactValue() {
        long[] weights = {30, 5, 5};
        long[] maxes = {30, 5, 5};
        for (int content = 1; content <= 30; content++) {
            for (int pron = 0; pron <= 5; pron++) {
                for (int fluency = 0; fluency <= 5; fluency++) {
                    ScoreTrace trace = RubricScorer.score(effective("READ_ALOUD"), new TraitScoreSet(
                            scores(RubricTraits.CONTENT, content, RubricTraits.PRONUNCIATION, pron,
                                    RubricTraits.ORAL_FLUENCY, fluency), 30));

                    long[] achieved = {content, pron, fluency};
                    assertThat(trace.itemScore()).isEqualTo(exactHalfUp(weights, achieved, maxes));
                    assertThat(trace.rawPercent())
                            .isCloseTo(exactPercent(weights, achieved, maxes), within(TOLERANCE));
                }
            }
        }
    }
}
