package com.pte.assessment.internal.service.definition;

import com.pte.assessment.dto.request.NormalizeSkillWeightsRequest;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse.SkillSuggestion;
import com.pte.assessment.dto.response.SkillWeightSuggestionResponse.TaskWeight;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/**
 * Suggests how to bring each skill column back to exactly 100 after tasks were removed. Pure computation:
 * it only redistributes among tasks that already carry weight in the column (or, when none does, among the tasks
 * of that skill's own section) and never zeroes a weight on its own.
 */
public final class SkillWeightNormalizer {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    private static final Map<String, Function<NormalizeSkillWeightsRequest.Item, BigDecimal>> COLUMNS =
            new LinkedHashMap<>();

    static {
        COLUMNS.put("SPEAKING", NormalizeSkillWeightsRequest.Item::speakingWeight);
        COLUMNS.put("WRITING", NormalizeSkillWeightsRequest.Item::writingWeight);
        COLUMNS.put("READING", NormalizeSkillWeightsRequest.Item::readingWeight);
        COLUMNS.put("LISTENING", NormalizeSkillWeightsRequest.Item::listeningWeight);
    }

    private SkillWeightNormalizer() {
    }

    public static SkillWeightSuggestionResponse suggest(List<NormalizeSkillWeightsRequest.Item> items) {
        List<SkillSuggestion> skills = new ArrayList<>();
        COLUMNS.forEach((skill, column) -> skills.add(suggestSkill(skill, column, items)));
        return new SkillWeightSuggestionResponse(skills);
    }

    private static SkillSuggestion suggestSkill(String skill,
            Function<NormalizeSkillWeightsRequest.Item, BigDecimal> column,
            List<NormalizeSkillWeightsRequest.Item> items) {
        Map<String, BigDecimal> current = new LinkedHashMap<>();
        for (var item : items) {
            current.merge(item.taskTypeKey(), weightOf(column, item), BigDecimal::add);
        }
        BigDecimal total = current.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean sectionHasTasks = items.stream().anyMatch(item -> skill.equals(upper(item.section())));
        boolean balanced = total.compareTo(HUNDRED) == 0 || (total.signum() == 0 && !sectionHasTasks);
        if (balanced) {
            return new SkillSuggestion(skill, total, HUNDRED.subtract(total), true, List.of(), List.of());
        }

        BigDecimal deficit = HUNDRED.subtract(total);
        Map<String, BigDecimal> weighted = new LinkedHashMap<>();
        current.forEach((key, weight) -> {
            if (weight.signum() > 0) {
                weighted.put(key, weight);
            }
        });
        if (weighted.isEmpty()) {
            // Nothing carries weight yet: spread over the tasks of the skill's own section.
            items.stream().filter(item -> skill.equals(upper(item.section())))
                    .forEach(item -> weighted.put(item.taskTypeKey(), BigDecimal.ZERO));
            Map<String, BigDecimal> equal = equalSplit(weighted, deficit);
            return new SkillSuggestion(skill, total, deficit, false, toList(equal), toList(equal));
        }
        return new SkillSuggestion(skill, total, deficit, false,
                toList(proportionalSplit(weighted, total)), toList(equalSplit(weighted, deficit)));
    }

    /** Scales every weight by {@code 100 / total}, keeping the old ratio between tasks. */
    private static Map<String, BigDecimal> proportionalSplit(Map<String, BigDecimal> weighted, BigDecimal total) {
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        weighted.forEach((key, weight) ->
                result.put(key, weight.multiply(HUNDRED).divide(total, SCALE, RoundingMode.HALF_UP)));
        return settleRounding(result);
    }

    /** Adds the same share of the deficit to each task; a task that would drop below 0 stops at 0. */
    private static Map<String, BigDecimal> equalSplit(Map<String, BigDecimal> weighted, BigDecimal deficit) {
        Map<String, BigDecimal> result = new LinkedHashMap<>(weighted);
        Map<String, BigDecimal> active = new LinkedHashMap<>(weighted);
        BigDecimal remaining = deficit;
        while (remaining.signum() != 0 && !active.isEmpty()) {
            BigDecimal share = remaining.divide(BigDecimal.valueOf(active.size()), SCALE + 4, RoundingMode.HALF_UP);
            BigDecimal applied = BigDecimal.ZERO;
            for (String key : new ArrayList<>(active.keySet())) {
                BigDecimal next = result.get(key).add(share);
                if (next.signum() < 0) {
                    applied = applied.subtract(result.get(key));
                    result.put(key, BigDecimal.ZERO);
                    active.remove(key);
                } else {
                    applied = applied.add(share);
                    result.put(key, next);
                }
            }
            remaining = remaining.subtract(applied);
            if (applied.signum() == 0 || share.signum() >= 0) {
                break;
            }
        }
        result.replaceAll((key, weight) -> weight.setScale(SCALE, RoundingMode.HALF_UP));
        return settleRounding(result);
    }

    /** Puts the cents lost to rounding on the largest weight so the column totals exactly 100. */
    private static Map<String, BigDecimal> settleRounding(Map<String, BigDecimal> weights) {
        BigDecimal sum = weights.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainder = HUNDRED.setScale(SCALE).subtract(sum);
        if (remainder.signum() != 0 && !weights.isEmpty()) {
            String largest = weights.entrySet().stream().max(Map.Entry.comparingByValue(Comparator.naturalOrder()))
                    .orElseThrow().getKey();
            weights.put(largest, weights.get(largest).add(remainder));
        }
        return weights;
    }

    private static List<TaskWeight> toList(Map<String, BigDecimal> weights) {
        return weights.entrySet().stream().map(e -> new TaskWeight(e.getKey(), e.getValue())).toList();
    }

    private static BigDecimal weightOf(Function<NormalizeSkillWeightsRequest.Item, BigDecimal> column,
            NormalizeSkillWeightsRequest.Item item) {
        BigDecimal value = column.apply(item);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
