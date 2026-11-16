package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import org.springframework.http.HttpStatus;

/**
 * The practice session is past its deadline. It is thrown right after the session was marked EXPIRED and saved, so the
 * transactions that can throw it list it in {@code noRollbackFor}: rolling back would undo the EXPIRED status the caller
 * has just been told about.
 */
public class PracticeSessionExpiredException extends PracticeSessionException {

    public PracticeSessionExpiredException() {
        super(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
    }
}
