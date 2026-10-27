package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** No template is ACTIVE — should never happen once V5 is seeded, but a snapshot publish must fail loudly, not pin nothing. */
public class NoActiveExamDefinitionException extends DomainException {

    public NoActiveExamDefinitionException() {
        super(HttpStatus.CONFLICT, ExamDefinitionConstants.NO_ACTIVE_TEMPLATE);
    }
}
