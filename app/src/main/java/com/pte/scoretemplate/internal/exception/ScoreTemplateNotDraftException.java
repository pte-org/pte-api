package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Attempted to edit or delete a template that is not DRAFT (ACTIVE/RETIRED are immutable). */
public class ScoreTemplateNotDraftException extends DomainException {

    public ScoreTemplateNotDraftException() {
        super(HttpStatus.CONFLICT, ScoreTemplateConstants.TEMPLATE_NOT_DRAFT);
    }
}
