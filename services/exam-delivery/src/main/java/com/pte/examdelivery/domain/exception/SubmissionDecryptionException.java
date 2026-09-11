package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** RSA-unwrap or AES-GCM auth-tag verification failed for a STRICT-pinned encrypted submission — tampered, corrupt, or malformed input. Never persists an AttemptAnswer. */
public class SubmissionDecryptionException extends DomainException {

    public SubmissionDecryptionException() {
        super(HttpStatus.BAD_REQUEST, ExamDeliveryConstants.SUBMISSION_DECRYPTION_FAILED);
    }
}
