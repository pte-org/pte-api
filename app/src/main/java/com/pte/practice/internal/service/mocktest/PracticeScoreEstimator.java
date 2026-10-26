package com.pte.practice.internal.service.mocktest;

import com.pte.itembank.TaskTypeCodeCompatibility;
import com.pte.practice.internal.dto.response.mocktest.PracticeEstimatedScoresResponse;
import com.pte.assessment.dto.response.ExamDefinitionItemResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Estimates the 10-90 skill and overall scores of a mock test from its item scores. The formula is the exam's
 * (FR-18 in {@code reporting.internal.service.ScoreAggregationService}): per skill
 * {@code 10 + 80 x sum(w x avgRaw / 100) / sum(w)}, where avgRaw is the mean raw score of a task type and a task
 * type with no score is left out of both sums. It is copied rather than called because practice may not use the
 * reporting module's internals; keep the two in step.
 *
 * <p>Differences from the exam, on purpose: a skipped or never-answered item counts as 0 (otherwise skipping most of
 * the test would still give a high score), and an item that failed to score is excluded and counted.
 */
@Component
public class PracticeScoreEstimator {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int FLOOR = 10;
    private static final int SPAN = 80;

    public enum ItemOutcome { SCORED, SCORING, FAILED, MISSING }

    /** One item of the test; {@code rawScore} (0-100) is read only for SCORED. */
    public record ScoredItem(String taskCode, ItemOutcome outcome, int rawScore) {
        public static ScoredItem scored(String taskCode, int rawScore) {
            return new ScoredItem(taskCode, ItemOutcome.SCORED, rawScore);
        }

        public static ScoredItem of(String taskCode, ItemOutcome outcome) {
            return new ScoredItem(taskCode, outcome, 0);
        }
    }

    public PracticeEstimatedScoresResponse estimate(List<ScoredItem> items,
            List<ExamDefinitionItemResponse> templateItems) {
        Map<String, BigDecimal> sumByType = new HashMap<>();
        Map<String, Integer> countByType = new HashMap<>();
        int skipped = 0;
        int excluded = 0;
        boolean pending = false;
        for (ScoredItem item : items) {
            String type = TaskTypeCodeCompatibility.normalizeTaskTypeKey(item.taskCode());
            switch (item.outcome()) {
                case SCORED -> add(sumByType, countByType, type, item.rawScore());
                case MISSING -> {
                    skipped++;
                    add(sumByType, countByType, type, 0);
                }
                case SCORING -> pending = true;
                case FAILED -> excluded++;
            }
        }
        Map<String, BigDecimal> avgByType = new HashMap<>();
        sumByType.forEach((type, sum) -> avgByType.put(type,
                sum.divide(BigDecimal.valueOf(countByType.get(type)), MATH)));
        return new PracticeEstimatedScoresResponse(
                weighted(templateItems, avgByType, ExamDefinitionItemResponse::overallWeight),
                weighted(templateItems, avgByType, ExamDefinitionItemResponse::speakingWeight),
                weighted(templateItems, avgByType, ExamDefinitionItemResponse::writingWeight),
                weighted(templateItems, avgByType, ExamDefinitionItemResponse::readingWeight),
                weighted(templateItems, avgByType, ExamDefinitionItemResponse::listeningWeight),
                skipped, excluded, pending);
    }

    private static void add(Map<String, BigDecimal> sums, Map<String, Integer> counts, String type, int raw) {
        sums.merge(type, BigDecimal.valueOf(raw), BigDecimal::add);
        counts.merge(type, 1, Integer::sum);
    }

    private Integer weighted(List<ExamDefinitionItemResponse> templateItems, Map<String, BigDecimal> avgByType,
            Function<ExamDefinitionItemResponse, BigDecimal> weightOf) {
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal totalWeight = BigDecimal.ZERO;
        for (ExamDefinitionItemResponse item : templateItems) {
            BigDecimal weight = weightOf.apply(item);
            if (weight == null || weight.signum() <= 0) {
                continue;
            }
            BigDecimal avg = avgByType.get(TaskTypeCodeCompatibility.normalizeTaskTypeKey(
                    item.taskTypeKey() == null ? item.taskType() : item.taskTypeKey()));
            if (avg == null) {
                continue;
            }
            weightedSum = weightedSum.add(weight.multiply(avg, MATH).divide(HUNDRED, MATH));
            totalWeight = totalWeight.add(weight);
        }
        if (totalWeight.signum() <= 0) {
            return null;
        }
        BigDecimal ratio = weightedSum.divide(totalWeight, MATH);
        return BigDecimal.valueOf(FLOOR).add(BigDecimal.valueOf(SPAN).multiply(ratio, MATH))
                .setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
