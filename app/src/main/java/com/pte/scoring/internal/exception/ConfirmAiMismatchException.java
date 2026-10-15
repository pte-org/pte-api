package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when CONFIRM_AI is chosen but the submitted score or viewed AI version does not match available AI. */
public class ConfirmAiMismatchException extends DomainException {

    public ConfirmAiMismatchException() {
        super(HttpStatus.CONFLICT, ExaminerScoringConstants.CONFIRM_AI_MISMATCH);
    }
}
