package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Attempted to edit or delete a template that is not DRAFT (ACTIVE/RETIRED are immutable). */
public class ExamDefinitionNotDraftException extends DomainException {

    public ExamDefinitionNotDraftException() {
        super(HttpStatus.CONFLICT, ExamDefinitionConstants.TEMPLATE_NOT_DRAFT_CODE, null,
                ExamDefinitionConstants.TEMPLATE_NOT_DRAFT, ExamDefinitionConstants.TEMPLATE_NOT_DRAFT_CODE);
    }
}
