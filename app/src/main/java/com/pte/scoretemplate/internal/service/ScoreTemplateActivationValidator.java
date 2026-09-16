package com.pte.scoretemplate.internal.service;

import com.pte.scoretemplate.domain.ScoreTemplate;
import com.pte.scoretemplate.domain.ScoreTemplateItem;
import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.scoretemplate.internal.exception.ScoreTemplateValidationException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * FR-05: structural soundness only — deliberately does NOT require weights
 * to sum to 100 (the source APEUni V5 table itself doesn't, due to
 * rounding). Stateless, no Spring context needed (mirrors this codebase's
 * other pure validators).
 */
public final class ScoreTemplateActivationValidator {

    /**
     * The 22 scored PTE task types. Duplicated here rather than sourced from
     * {@code itembank.PteTaskType} on purpose — {@code scoretemplate} stays
     * independent of {@code itembank} (Spring Modulith boundary), mirroring
     * how {@code ScoreTemplateItem.taskType} is a plain String.
     */
    private static final Set<String> REQUIRED_TASK_TYPES = Set.of(
            "READ_ALOUD", "REPEAT_SENTENCE", "DESCRIBE_IMAGE", "RE_TELL_LECTURE", "ANSWER_SHORT_QUESTION",
            "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY",
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_BLANKS_READING",
            "FILL_BLANKS_READING_WRITING",
            "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_BLANKS_LISTENING",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");

    private static final Map<String, Function<ScoreTemplateItem, BigDecimal>> WEIGHT_COLUMNS = new LinkedHashMap<>();

    static {
        WEIGHT_COLUMNS.put("OVERALL", ScoreTemplateItem::getOverallWeight);
        WEIGHT_COLUMNS.put("SPEAKING", ScoreTemplateItem::getSpeakingWeight);
        WEIGHT_COLUMNS.put("WRITING", ScoreTemplateItem::getWritingWeight);
        WEIGHT_COLUMNS.put("READING", ScoreTemplateItem::getReadingWeight);
        WEIGHT_COLUMNS.put("LISTENING", ScoreTemplateItem::getListeningWeight);
    }

    private ScoreTemplateActivationValidator() {
    }

    public static void validate(ScoreTemplate template) {
        List<ScoreTemplateItem> items = template.getItems();
        if (items == null || items.isEmpty()) {
            throw new ScoreTemplateValidationException(ScoreTemplateConstants.ITEMS_REQUIRED);
        }

        validateAllRequiredTaskTypesPresent(items);
        items.forEach(ScoreTemplateActivationValidator::validateCountRange);
        items.forEach(ScoreTemplateActivationValidator::validateWeightsNonNegative);
        validateEverySkillHasPositiveTotalWeight(items);
    }

    private static void validateAllRequiredTaskTypesPresent(List<ScoreTemplateItem> items) {
        Set<String> present = new LinkedHashSet<>();
        items.forEach(item -> present.add(item.getTaskType()));
        Set<String> missing = new LinkedHashSet<>(REQUIRED_TASK_TYPES);
        missing.removeAll(present);
        if (!missing.isEmpty()) {
            throw new ScoreTemplateValidationException(ScoreTemplateConstants.MISSING_TASK_TYPES + missing);
        }
    }

    private static void validateCountRange(ScoreTemplateItem item) {
        if (item.getMinCount() < 0 || item.getMinCount() > item.getMaxCount() || item.getMaxCount() < 1) {
            throw new ScoreTemplateValidationException(ScoreTemplateConstants.INVALID_COUNT_RANGE + item.getTaskType());
        }
    }

    private static void validateWeightsNonNegative(ScoreTemplateItem item) {
        for (Function<ScoreTemplateItem, BigDecimal> accessor : WEIGHT_COLUMNS.values()) {
            if (accessor.apply(item).signum() < 0) {
                throw new ScoreTemplateValidationException(ScoreTemplateConstants.NEGATIVE_WEIGHT + item.getTaskType());
            }
        }
    }

    private static void validateEverySkillHasPositiveTotalWeight(List<ScoreTemplateItem> items) {
        WEIGHT_COLUMNS.forEach((skillName, accessor) -> {
            BigDecimal total = items.stream().map(accessor).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.signum() <= 0) {
                throw new ScoreTemplateValidationException(ScoreTemplateConstants.SKILL_WITH_NO_WEIGHT + skillName);
            }
        });
    }
}
