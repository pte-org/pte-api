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
}
