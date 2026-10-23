package com.pte.rubric.internal.exception;

import com.pte.rubric.internal.constant.RubricConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class RubricNotFoundException extends DomainException {
    public RubricNotFoundException() {
        super(HttpStatus.NOT_FOUND, RubricConstants.RUBRIC_NOT_FOUND);
    }
}
