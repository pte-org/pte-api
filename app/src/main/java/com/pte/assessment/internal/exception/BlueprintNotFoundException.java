package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BlueprintNotFoundException extends DomainException {

    public BlueprintNotFoundException() {
        super(HttpStatus.NOT_FOUND, AssessmentConstants.BLUEPRINT_NOT_FOUND);
    }
}
