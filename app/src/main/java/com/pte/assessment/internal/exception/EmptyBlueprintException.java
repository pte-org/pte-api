package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Attempted to publish a blueprint with no items. */
public class EmptyBlueprintException extends DomainException {

    public EmptyBlueprintException() {
        super(HttpStatus.BAD_REQUEST, AssessmentConstants.EMPTY_BLUEPRINT);
    }
}
