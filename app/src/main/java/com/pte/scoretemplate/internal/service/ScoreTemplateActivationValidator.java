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
 * FR-05: structural soundness, plus an exact-100 check on each of the 4
 * skill weight columns (Speaking/Writing/Reading/Listening) — the updated
 * APEUni V5 table sums each of those to exactly 100 (unlike the original
 * table, which didn't, due to rounding), so this is now enforced at
 * activation time to catch admin data-entry mistakes. {@code OVERALL} is
 * deliberately exempt — it's still just required to be positive per skill,
 * never required to sum to a fixed total. Stateless, no Spring context
 * needed (mirrors this codebase's other pure validators).
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
            "MC_READING_SINGLE", "MC_READING_MULTIPLE", "RE_ORDER_PARAGRAPHS", "FILL_IN_THE_BLANKS_DRAG_AND_DROP",
            "FILL_IN_THE_BLANKS_DROPDOWN",
            "SUMMARIZE_SPOKEN_TEXT", "MC_LISTENING_SINGLE", "MC_LISTENING_MULTIPLE", "FILL_IN_THE_BLANKS_TYPE_IN",
            "HIGHLIGHT_CORRECT_SUMMARY", "SELECT_MISSING_WORD", "HIGHLIGHT_INCORRECT_WORDS", "WRITE_FROM_DICTATION");

    private static final Map<String, Function<ScoreTemplateItem, BigDecimal>> WEIGHT_COLUMNS = new LinkedHashMap<>();

    /** The 4 skill columns required to sum to exactly 100 — {@code OVERALL} is not one of them. */
    private static final Map<String, Function<ScoreTemplateItem, BigDecimal>> SKILL_WEIGHT_COLUMNS = new LinkedHashMap<>();

    private static final BigDecimal REQUIRED_SKILL_TOTAL = BigDecimal.valueOf(100);

    static {
        WEIGHT_COLUMNS.put("OVERALL", ScoreTemplateItem::getOverallWeight);
        WEIGHT_COLUMNS.put("SPEAKING", ScoreTemplateItem::getSpeakingWeight);
        WEIGHT_COLUMNS.put("WRITING", ScoreTemplateItem::getWritingWeight);
        WEIGHT_COLUMNS.put("READING", ScoreTemplateItem::getReadingWeight);
        WEIGHT_COLUMNS.put("LISTENING", ScoreTemplateItem::getListeningWeight);

        SKILL_WEIGHT_COLUMNS.put("SPEAKING", ScoreTemplateItem::getSpeakingWeight);
        SKILL_WEIGHT_COLUMNS.put("WRITING", ScoreTemplateItem::getWritingWeight);
        SKILL_WEIGHT_COLUMNS.put("READING", ScoreTemplateItem::getReadingWeight);
        SKILL_WEIGHT_COLUMNS.put("LISTENING", ScoreTemplateItem::getListeningWeight);
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
        validateSkillWeightsSumToExactly100(items);
    }

    /**
     * Just the exact-100 skill-weight check, exposed separately so
     * {@code ScoreTemplateAdminService.replaceItems} (every "Save", not only
     * "Activate") can enforce it eagerly — unlike {@link #validate}, this
     * does not also require every task type present or count ranges valid,
     * since a DRAFT's item list is always the full 22-row set to begin with
     * (cloned, never authored row-by-row) and only the weight columns are
     * actually hand-edited. A still-empty DRAFT (name saved before any
     * items exist, FR-02) is exempt — there's nothing to total yet.
     */
    public static void validateSkillWeightTotals(ScoreTemplate template) {
        List<ScoreTemplateItem> items = template.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        validateSkillWeightsSumToExactly100(items);
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

    /** Speaking/Writing/Reading/Listening must each sum to exactly 100 — no more, no less. {@code OVERALL} is exempt (see class Javadoc). */
    private static void validateSkillWeightsSumToExactly100(List<ScoreTemplateItem> items) {
        SKILL_WEIGHT_COLUMNS.forEach((skillName, accessor) -> {
            BigDecimal total = items.stream().map(accessor).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (total.compareTo(REQUIRED_SKILL_TOTAL) != 0) {
                throw new ScoreTemplateValidationException(
                        ScoreTemplateConstants.SKILL_WEIGHT_NOT_100 + skillName + " (current total: " + total + ")");
            }
        });
    }
}
