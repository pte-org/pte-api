package com.pte.itembank;

import java.util.List;
import java.util.Optional;

/**
 * SPI through which the scoring module describes the prompts it sends to the AI scorer, for the AI admin pages.
 * Same reason as {@link GenerationRubricTraits}: itembank must not depend on scoring, so scoring implements this.
 */
public interface ScoringPromptSource {

    /** One entry per task type scored by the rubric catalog. */
    List<PromptDocument.Summary> summaries();

    /** The scoring prompt of a task type, if the catalog has it. */
    Optional<PromptDocument> find(String taskTypeKey);
}
