package com.pte.scoring.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidExaminerAssignmentException extends DomainException {

    public InvalidExaminerAssignmentException(String reason) {
        super(HttpStatus.BAD_REQUEST, "INVALID_EXAMINER_ASSIGNMENT", null,
                "The Examiner assignment request is invalid.", reason);
    }
}
