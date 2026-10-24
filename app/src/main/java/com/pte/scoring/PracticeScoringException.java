package com.pte.scoring;

/**
 * A practice answer could not be scored. The message is the failure code only,
 * never the candidate's answer or a vendor response, because it reaches logs.
 */
public class PracticeScoringException extends RuntimeException {

    private final PracticeScoringFailure failure;

    public PracticeScoringException(PracticeScoringFailure failure) {
        super(failure.name());
        this.failure = failure;
    }

    public PracticeScoringException(PracticeScoringFailure failure, Throwable cause) {
        super(failure.name(), cause);
        this.failure = failure;
    }

    public PracticeScoringFailure failure() {
        return failure;
    }
}
