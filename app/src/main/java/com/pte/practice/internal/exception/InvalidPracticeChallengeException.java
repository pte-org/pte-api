package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Generic challenge failure that does not disclose which verification step failed. */
public class InvalidPracticeChallengeException extends DomainException {

    public InvalidPracticeChallengeException() {
        super(HttpStatus.UNAUTHORIZED, PracticeConstants.PRACTICE_INVALID_CHALLENGE);
    }
}
