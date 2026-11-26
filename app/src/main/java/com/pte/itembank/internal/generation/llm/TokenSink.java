package com.pte.itembank.internal.generation.llm;

/** Receives the text of one provider call while the model writes it, for the live view of a job. */
public interface TokenSink {

    /** Nobody watches: the gateway reads the whole answer at once. */
    TokenSink NONE = new TokenSink() {
        @Override
        public void begin() {
        }

        @Override
        public void accept(String delta) {
        }
    };

    /** A new call (or a retry of one) starts: what was written before is no longer the answer. */
    void begin();

    void accept(String delta);

    /**
     * A piece of what the model thinks before (or while) it writes the answer, for a model that streams its reasoning.
     * It is never part of the answer; by default nobody reads it.
     */
    default void thinking(String delta) {
    }
}
