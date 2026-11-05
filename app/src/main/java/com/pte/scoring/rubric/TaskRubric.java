package com.pte.scoring.rubric;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The rubric of one task type as declared in code from the Score Guide. Trait order is the Score Guide's order;
 * a gate trait scoring 0 zeroes the whole item. {@code formRule} is set only for the three writing tasks with a
 * Form criterion, {@code objectiveRule} only for answer-key task types (which have no traits).
 */
public record TaskRubric(String taskType, Set<String> skills, List<TraitDefinition> traits, Set<String> gateTraits,
                         FormRule formRule, ObjectiveRule objectiveRule, String pearsonPages) {

    public TaskRubric {
        skills = Set.copyOf(skills);
        traits = List.copyOf(traits);
        gateTraits = Set.copyOf(gateTraits);
    }

    public Optional<TraitDefinition> trait(String key) {
        return traits.stream().filter(trait -> trait.key().equals(key)).findFirst();
    }

    public boolean isObjective() {
        return objectiveRule != null;
    }
}
