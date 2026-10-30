package com.pte.assessment.internal.exception;

import com.pte.assessment.internal.constant.ExamDefinitionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Translates a {@code DataIntegrityViolationException} from the partial
 * unique ACTIVE index or the unique {@code (code, version)} index into a
 * clean, retryable 409 — surfaced when two admins race on
 * {@code activate}/{@code cloneToDraft} for the same template code.
 */
public class ExamDefinitionConcurrentModificationException extends DomainException {

    public ExamDefinitionConcurrentModificationException() {
        super(HttpStatus.CONFLICT, ExamDefinitionConstants.CONCURRENT_MODIFICATION);
    }
}
