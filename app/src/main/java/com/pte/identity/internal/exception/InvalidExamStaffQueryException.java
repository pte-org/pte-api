package com.pte.identity.internal.exception;

import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Rejects values outside the public Exam Staff list query contract. */
public class InvalidExamStaffQueryException extends DomainException {

    public InvalidExamStaffQueryException() {
        super(HttpStatus.BAD_REQUEST, IdentityConstants.INVALID_EXAM_STAFF_QUERY);
    }
}
