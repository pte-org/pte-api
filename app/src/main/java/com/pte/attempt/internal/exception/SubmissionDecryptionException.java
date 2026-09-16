package com.pte.attempt.internal.exception;

import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** RSA-unwrap or AES-GCM auth-tag verification failed for a STRICT-pinned encrypted submission — tampered, corrupt, or malformed input. Never persists an AttemptAnswer. */
public class SubmissionDecryptionException extends DomainException {

    public SubmissionDecryptionException() {
        super(HttpStatus.BAD_REQUEST, AttemptConstants.SUBMISSION_DECRYPTION_FAILED);
    }
}
