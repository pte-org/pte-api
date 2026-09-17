package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.AssessmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** {@code skills} was empty or had more than 4 distinct sections. */
public class InvalidSkillSelectionException extends DomainException {

    public InvalidSkillSelectionException() {
        super(HttpStatus.BAD_REQUEST, AssessmentConstants.INVALID_SKILL_SELECTION);
    }
}
