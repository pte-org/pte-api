package com.pte.rubric;

/** Public module-boundary error used when Practice cannot obtain a usable published rubric. */
public class RubricUnavailableException extends RuntimeException {

    public RubricUnavailableException(Throwable cause) {
        super("PRACTICE_RUBRIC_UNAVAILABLE", cause);
    }
}
