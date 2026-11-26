package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.exception.GenerationCallBudgetExceededException;

/**
 * Hard allowance of provider calls for ONE question. Not thread-safe: one budget per question, one thread.
 * {@code beforeEachCall} runs just before every allowed call; the job runner renews its lease there, because a slow
 * provider (a free fallback model may answer only after a minute) would otherwise outlast the lease within one question.
 */
public final class GenerationCallBudget {

    private static final Runnable NOTHING_BEFORE_CALL = () -> { };

    private final Runnable beforeEachCall;
    private int remaining;

    public GenerationCallBudget(int maxCalls) {
        this(maxCalls, NOTHING_BEFORE_CALL);
    }

    public GenerationCallBudget(int maxCalls, Runnable beforeEachCall) {
        this.remaining = maxCalls;
        this.beforeEachCall = beforeEachCall;
    }

    public void consume() {
        if (remaining <= 0) {
            throw new GenerationCallBudgetExceededException();
        }
        remaining--;
        beforeEachCall.run();
    }

    public int remaining() {
        return remaining;
    }
}
