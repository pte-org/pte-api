package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Translates a {@code DataIntegrityViolationException} from the partial
 * unique ACTIVE index or the unique {@code (code, version)} index into a
 * clean, retryable 409 — surfaced when two admins race on
 * {@code activate}/{@code cloneToDraft} for the same template code.
 */
public class ScoreTemplateConcurrentModificationException extends DomainException {

    public ScoreTemplateConcurrentModificationException() {
        super(HttpStatus.CONFLICT, ScoreTemplateConstants.CONCURRENT_MODIFICATION);
    }
}
