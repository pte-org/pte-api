package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Two rows in the same bulk-create request share an email — the whole batch is rejected, nothing written. */
public class DuplicateEmailInBatchException extends DomainException {

    public DuplicateEmailInBatchException() {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.DUPLICATE_EMAIL_IN_BATCH);
    }
}
