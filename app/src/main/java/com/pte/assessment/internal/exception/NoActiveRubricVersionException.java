package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A new exam definition cannot be created: it must pin a rubric version and no rubric version is ACTIVE. */
public class NoActiveRubricVersionException extends DomainException {

    public NoActiveRubricVersionException() {
        super(HttpStatus.CONFLICT, ExamDefinitionConstants.NO_ACTIVE_RUBRIC_VERSION);
    }
}
