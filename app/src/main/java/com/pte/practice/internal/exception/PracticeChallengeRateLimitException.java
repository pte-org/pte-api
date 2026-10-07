package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Too many challenge requests for the normalized email in the current window. */
public class PracticeChallengeRateLimitException extends DomainException {

    public PracticeChallengeRateLimitException() {
        super(HttpStatus.TOO_MANY_REQUESTS, PracticeConstants.PRACTICE_CHALLENGE_RATE_LIMITED);
    }
}
