package com.pte.assessment.internal.service.definition;

import com.pte.assessment.domain.ExamDefinition;
import com.pte.assessment.domain.ExamDefinitionItem;
import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.assessment.internal.exception.ExamDefinitionValidationException;

import java.math.BigDecimal;
import java.util.ArrayList;
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
public final class ExamDefinitionActivationValidator {

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

    private static final Map<String, Function<ExamDefinitionItem, BigDecimal>> WEIGHT_COLUMNS = new LinkedHashMap<>();

    /** The 4 skill columns required to sum to exactly 100 — {@code OVERALL} is not one of them. */
    private static final Map<String, Function<ExamDefinitionItem, BigDecimal>> SKILL_WEIGHT_COLUMNS = new LinkedHashMap<>();

    private static final BigDecimal REQUIRED_SKILL_TOTAL = BigDecimal.valueOf(100);

    static {
        WEIGHT_COLUMNS.put("OVERALL", ExamDefinitionItem::getOverallWeight);
        WEIGHT_COLUMNS.put("SPEAKING", ExamDefinitionItem::getSpeakingWeight);
        WEIGHT_COLUMNS.put("WRITING", ExamDefinitionItem::getWritingWeight);
        WEIGHT_COLUMNS.put("READING", ExamDefinitionItem::getReadingWeight);
        WEIGHT_COLUMNS.put("LISTENING", ExamDefinitionItem::getListeningWeight);

        SKILL_WEIGHT_COLUMNS.put("SPEAKING", ExamDefinitionItem::getSpeakingWeight);
        SKILL_WEIGHT_COLUMNS.put("WRITING", ExamDefinitionItem::getWritingWeight);
        SKILL_WEIGHT_COLUMNS.put("READING", ExamDefinitionItem::getReadingWeight);
        SKILL_WEIGHT_COLUMNS.put("LISTENING", ExamDefinitionItem::getListeningWeight);
    }

    private ExamDefinitionActivationValidator() {
    }

    public static void validate(ExamDefinition template) {
        List<ExamDefinitionItem> items = template.getItems();
        if (items == null || items.isEmpty()) {
            throw new ExamDefinitionValidationException(ExamDefinitionConstants.ITEMS_REQUIRED);
        }

        List<String> errors = new ArrayList<>();
        boolean custom = template.getTemplatePolicy() != null
                && "CUSTOM".equals(template.getTemplatePolicy().name());
        if (custom) {
            collectCustomTaskTypeErrors(items, errors);
        } else {
            collectTaskTypeErrors(items, errors);
        }
        collectSequenceErrors(items, errors);
        items.forEach(item -> collectCountError(item, errors));
        items.forEach(item -> collectWeightErrors(item, errors));
        if (custom) {
            collectCustomScoringErrors(items, errors);
        } else {
            collectPositiveWeightErrors(items, errors);
            collectSkillWeightTotalErrors(items, errors);
        }
        if (!errors.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", errors));
        }
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
    public static void validateSkillWeightTotals(ExamDefinition template) {
        List<ExamDefinitionItem> items = template.getItems();
        if (items == null || items.isEmpty()) {
            return;
        }
        List<String> errors = new ArrayList<>();
        if (template.getTemplatePolicy() != null && "CUSTOM".equals(template.getTemplatePolicy().name())) {
            collectCustomScoringErrors(items, errors);
        } else {
            collectSkillWeightTotalErrors(items, errors);
        }
        if (!errors.isEmpty()) {
            throw new ExamDefinitionValidationException(String.join("; ", errors));
        }
    }

    private static void collectTaskTypeErrors(List<ExamDefinitionItem> items, List<String> errors) {
        Set<String> present = new LinkedHashSet<>();
        items.forEach(item -> present.add(logicalTaskKey(item)));
        Set<String> missing = new LinkedHashSet<>(REQUIRED_TASK_TYPES);
        missing.removeAll(present);
        if (!missing.isEmpty()) {
            errors.add(ExamDefinitionConstants.MISSING_TASK_TYPES + missing);
        }
        Set<String> duplicates = new LinkedHashSet<>();
        Set<String> seen = new LinkedHashSet<>();
        items.forEach(item -> {
            if (!seen.add(item.getTaskType())) {
                duplicates.add(logicalTaskKey(item));
            }
        });
        if (!duplicates.isEmpty()) {
            errors.add(ExamDefinitionConstants.DUPLICATE_TASK_TYPES + duplicates);
        }
    }

    private static void collectCustomTaskTypeErrors(List<ExamDefinitionItem> items, List<String> errors) {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (ExamDefinitionItem item : items) {
            String key = logicalTaskKey(item);
            if (key == null || key.isBlank()) {
                errors.add(ExamDefinitionConstants.TEMPLATE_TASK_TYPE_INVALID + "(missing key)");
            } else if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            errors.add(ExamDefinitionConstants.DUPLICATE_TASK_TYPES + duplicates);
        }
    }

    private static void collectSequenceErrors(List<ExamDefinitionItem> items, List<String> errors) {
        Set<Integer> seen = new LinkedHashSet<>();
        Set<Integer> duplicates = new LinkedHashSet<>();
        for (ExamDefinitionItem item : items) {
            if (item.getSequence() < 0) {
                errors.add(ExamDefinitionConstants.INVALID_SEQUENCE + item.getSequence());
            } else if (!seen.add(item.getSequence())) {
                duplicates.add(item.getSequence());
            }
        }
        if (!duplicates.isEmpty()) {
            errors.add(ExamDefinitionConstants.DUPLICATE_SEQUENCES + duplicates);
        }
    }

    private static void collectCountError(ExamDefinitionItem item, List<String> errors) {
        if (item.getMinCount() < 0 || item.getMinCount() > item.getMaxCount() || item.getMaxCount() < 1) {
            errors.add(ExamDefinitionConstants.INVALID_COUNT_RANGE + item.getTaskType());
        }
    }

    private static void collectWeightErrors(ExamDefinitionItem item, List<String> errors) {
        for (Function<ExamDefinitionItem, BigDecimal> accessor : WEIGHT_COLUMNS.values()) {
            BigDecimal value = accessor.apply(item);
            if (value == null || value.signum() < 0) {
                errors.add(ExamDefinitionConstants.NEGATIVE_WEIGHT + logicalTaskKey(item));
                return;
            }
            if (value.compareTo(BigDecimal.valueOf(100)) > 0) {
                errors.add(ExamDefinitionConstants.TEMPLATE_WEIGHT_OUT_OF_RANGE + logicalTaskKey(item));
            }
            if (value.scale() > 2) {
                errors.add(ExamDefinitionConstants.TEMPLATE_WEIGHT_PRECISION + logicalTaskKey(item));
            }
        }
    }

    private static void collectCustomScoringErrors(List<ExamDefinitionItem> items, List<String> errors) {
        boolean anyScored = false;
        for (ExamDefinitionItem item : items) {
            var profile = item.pinnedRuntimeProfile();
            boolean scored = profile != null && profile.scoringEnabled();
            anyScored |= scored;
            if (!scored && hasPositiveSkillWeight(item)) {
                errors.add(ExamDefinitionConstants.TEMPLATE_UNSCORED_WEIGHT + logicalTaskKey(item));
            }
        }
        if (!anyScored) {
            errors.add(ExamDefinitionConstants.TEMPLATE_NO_SCORED_TASKS);
            return;
        }
        Map<String, Function<ExamDefinitionItem, BigDecimal>> columns = SKILL_WEIGHT_COLUMNS;
        columns.forEach((skill, accessor) -> {
            BigDecimal total = items.stream()
                    .filter(item -> item.pinnedRuntimeProfile() != null
                            && item.pinnedRuntimeProfile().scoringEnabled())
                    .map(accessor).map(value -> value == null ? BigDecimal.ZERO : value)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean represented = items.stream()
                    .filter(item -> item.pinnedRuntimeProfile() != null
                            && item.pinnedRuntimeProfile().scoringEnabled())
                    .map(accessor).anyMatch(value -> value != null && value.signum() > 0);
            if (represented && total.compareTo(REQUIRED_SKILL_TOTAL) != 0) {
                errors.add(ExamDefinitionConstants.SKILL_WEIGHT_NOT_100 + skill + " (current total: " + total + ")");
            } else if (!represented && total.signum() != 0) {
                errors.add(ExamDefinitionConstants.SKILL_WITH_NO_WEIGHT + skill);
            }
        });
        BigDecimal overall = sum(items, ExamDefinitionItem::getOverallWeight);
        if (overall.signum() <= 0) {
            errors.add(ExamDefinitionConstants.SKILL_WITH_NO_WEIGHT + "OVERALL");
        }
    }

    private static boolean hasPositiveSkillWeight(ExamDefinitionItem item) {
        return item.getSpeakingWeight().signum() > 0 || item.getWritingWeight().signum() > 0
                || item.getReadingWeight().signum() > 0 || item.getListeningWeight().signum() > 0
                || item.getOverallWeight().signum() > 0;
    }

    private static String logicalTaskKey(ExamDefinitionItem item) {
        return item.getTaskTypeKey() == null ? item.getTaskType() : item.getTaskTypeKey();
    }

    private static void collectPositiveWeightErrors(List<ExamDefinitionItem> items, List<String> errors) {
        WEIGHT_COLUMNS.forEach((skillName, accessor) -> {
            BigDecimal total = sum(items, accessor);
            if (total.signum() <= 0) {
                errors.add(ExamDefinitionConstants.SKILL_WITH_NO_WEIGHT + skillName);
            }
        });
    }

    /** Speaking/Writing/Reading/Listening must each sum to exactly 100 — no more, no less. {@code OVERALL} is exempt (see class Javadoc). */
    private static void collectSkillWeightTotalErrors(List<ExamDefinitionItem> items, List<String> errors) {
        SKILL_WEIGHT_COLUMNS.forEach((skillName, accessor) -> {
            BigDecimal total = sum(items, accessor);
            if (total.compareTo(REQUIRED_SKILL_TOTAL) != 0) {
                errors.add(ExamDefinitionConstants.SKILL_WEIGHT_NOT_100 + skillName + " (current total: " + total + ")");
            }
        });
    }

    private static BigDecimal sum(List<ExamDefinitionItem> items, Function<ExamDefinitionItem, BigDecimal> accessor) {
        return items.stream()
                .map(accessor)
                .map(value -> value == null ? BigDecimal.ZERO : value)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
