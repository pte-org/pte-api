package com.pte.scoretemplate.internal.exception;

import com.pte.scoretemplate.internal.constant.ScoreTemplateConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** No template is ACTIVE — should never happen once V5 is seeded, but a snapshot publish must fail loudly, not pin nothing. */
public class NoActiveScoreTemplateException extends DomainException {

    public NoActiveScoreTemplateException() {
        super(HttpStatus.CONFLICT, ScoreTemplateConstants.NO_ACTIVE_TEMPLATE);
    }
}
