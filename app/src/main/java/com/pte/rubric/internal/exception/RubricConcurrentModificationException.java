package com.pte.rubric.internal.exception;

import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RubricConcurrentModificationException extends DomainException {
    public RubricConcurrentModificationException() {
        super(HttpStatus.CONFLICT, RubricConstants.RUBRIC_CONCURRENT_MODIFICATION);
    }
}
