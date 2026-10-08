package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import org.springframework.http.HttpStatus;

/** Hides another student's practice sessions behind the same 404 contract. */
public class PracticeSessionNotFoundException extends PracticeSessionException {

    public PracticeSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, PracticeConstants.PRACTICE_SESSION_NOT_FOUND,
                PracticeConstants.PRACTICE_SESSION_NOT_FOUND);
    }
}
