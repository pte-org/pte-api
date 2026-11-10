package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;

/** Hard allowance of provider calls for ONE question. Not thread-safe: one budget per question, one thread. */
public final class GenerationCallBudget {

    private int remaining;

    public GenerationCallBudget(int maxCalls) {
        this.remaining = maxCalls;
    }

    public void consume() {
        if (remaining <= 0) {
            throw new GenerationCallBudgetExceededException();
        }
        remaining--;
    }

    public int remaining() {
        return remaining;
    }
}
