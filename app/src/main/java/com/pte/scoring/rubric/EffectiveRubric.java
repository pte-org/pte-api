package com.pte.scoring.rubric;

import com.pte.scoring.internal.constant.RubricConstants;

import java.util.Comparator;
import java.util.List;

/**
 * A task rubric with the parameters of one rubric version applied. Built from the stored version's parameters,
 * never from code defaults at scoring time. Construction validates the weights: every trait has an integer weight
 * in 0-10, every catalog gate trait weighs more than 0 even when disabled, and the weights do not sum to 0.
 */
public record EffectiveRubric(TaskRubric task, RubricParameters parameters) {

    public EffectiveRubric {
        if (!task.isObjective()) {
            validateWeights(task, parameters);
        }
        validateFormBands(task, parameters);
        validateGateKeys(task, parameters);
    }

    public FormRule effectiveFormRule() {
        FormRule rule = task.formRule();
        if (rule == null || parameters.formBands() == null) {
            return rule;
        }
        return rule.withBands(parameters.formBands());
    }

    /**
     * A trait's maximum for one answer: Form's is the highest band of the effective Form rule (so edited bands stay
     * consistent), a per-word trait's is the prompt's word count, any other trait's is its fixed Pearson scale.
     */
    public int maxOf(TraitDefinition trait, Integer wordCount) {
        FormRule formRule = effectiveFormRule();
        if (RubricTraits.FORM.equals(trait.key()) && formRule != null) {
            return formRule.maxBand();
        }
        return trait.scale().maxFor(wordCount);
    }

    public int weightOf(String traitKey) {
        return parameters.traitWeights().get(traitKey);
    }

    /** A gate is on unless the parameters switch it off explicitly. */
    public boolean isGateEnabled(String traitKey) {
        return isGateOn(task, parameters, traitKey);
    }

    private static boolean isGateOn(TaskRubric task, RubricParameters parameters, String traitKey) {
        return task.gateTraits().contains(traitKey) && !Boolean.FALSE.equals(parameters.gateEnabled().get(traitKey));
    }

    /**
     * Form bands exist exactly when the task has a Form rule; they must be non-empty, each with a band of 0 or
     * more and minWords not above maxWords, must not overlap, and at least one must earn a positive Form score.
     */
    private static void validateFormBands(TaskRubric task, RubricParameters parameters) {
        List<FormBand> bands = parameters.formBands();
        if ((task.formRule() == null) != (bands == null)) {
            throw invalidBands();
        }
        if (bands == null) {
            return;
        }
        if (bands.isEmpty() || bands.stream().noneMatch(band -> band.band() > 0)) {
            throw invalidBands();
        }
        for (FormBand band : bands) {
            if (band.band() < 0 || band.minWords() < 0 || band.minWords() > band.maxWords()) {
                throw invalidBands();
            }
        }
        List<FormBand> sorted = bands.stream().sorted(Comparator.comparingInt(FormBand::minWords)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i).minWords() <= sorted.get(i - 1).maxWords()) {
                throw invalidBands();
            }
        }
    }

    private static void validateGateKeys(TaskRubric task, RubricParameters parameters) {
        if (!task.gateTraits().containsAll(parameters.gateEnabled().keySet())) {
            throw invalidWeight();
        }
    }

    private static RubricScoreException invalidBands() {
        return new RubricScoreException(RubricConstants.RUBRIC_FORM_BANDS_INVALID);
    }

    private static void validateWeights(TaskRubric task, RubricParameters parameters) {
        if (parameters.traitWeights().size() != task.traits().size()) {
            throw invalidWeight();
        }
        int sum = 0;
        for (TraitDefinition trait : task.traits()) {
            Integer weight = parameters.traitWeights().get(trait.key());
            if (weight == null || weight < RubricConstants.MIN_TRAIT_WEIGHT
                    || weight > RubricConstants.MAX_TRAIT_WEIGHT) {
                throw invalidWeight();
            }
            if (task.gateTraits().contains(trait.key()) && weight == 0) {
                throw invalidWeight();
            }
            sum += weight;
        }
        if (sum == 0) {
            throw invalidWeight();
        }
    }

    private static RubricScoreException invalidWeight() {
        return new RubricScoreException(RubricConstants.RUBRIC_WEIGHT_INVALID);
    }
}
