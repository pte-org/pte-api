package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A blueprint item's {@code section} field didn't match a known {@code PteSection}. */
public class InvalidSectionException extends DomainException {

    public InvalidSectionException() {
        super(HttpStatus.BAD_REQUEST, AssessmentConstants.INVALID_SECTION);
    }
}
