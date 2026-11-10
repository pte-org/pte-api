package com.pte.itembank.internal.generation.pipeline;

/** Produces the draft of one question. Real generators call the LLM; the stub never does. */
public interface ItemGenerator {

    boolean supports(String taskTypeKey);

    /**
     * Every provider call must first {@link GenerationCallBudget#consume() consume} the budget, so one question can
     * never run away with the allowance.
     */
    GeneratedItem generate(GenerationRequest request, GenerationCallBudget budget);
}
