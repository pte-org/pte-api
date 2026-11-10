package com.pte.scoring.rubric;

import java.util.List;
import java.util.Optional;

/**
 * The rubrics of the 22 standard task types, declared in code from the Pearson Score Guide. Level descriptions and
 * scales are fixed here; {@link #defaultParameters} only seeds a new rubric version and is never consulted when
 * scoring an answer.
 */
public interface RubricCatalog {

    Optional<TaskRubric> find(String taskType);

    List<TaskRubric> all();

    int catalogVersion();

    /** Weights equal to each trait's maximum (1 per word for a per-word trait), catalog Form bands, all gates on. */
    RubricParameters defaultParameters(String taskType);
}
