package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/** Two rows in the same bulk-create request share an email — the whole batch is rejected, nothing written. */
public class DuplicateEmailInBatchException extends DomainException {

    public DuplicateEmailInBatchException() {
        super(HttpStatus.BAD_REQUEST, IamConstants.DUPLICATE_EMAIL_IN_BATCH);
    }
}
