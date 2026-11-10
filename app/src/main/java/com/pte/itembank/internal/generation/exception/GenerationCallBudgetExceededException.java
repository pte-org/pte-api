package com.pte.itembank.internal.generation.exception;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;

/** One question used up its hard allowance of provider calls; the item fails, the job continues. */
public class GenerationCallBudgetExceededException extends RuntimeException {

    public GenerationCallBudgetExceededException() {
        super(QuestionGenerationConstants.GENERATION_CALL_BUDGET_EXCEEDED);
    }
}
