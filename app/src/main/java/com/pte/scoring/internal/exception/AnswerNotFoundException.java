package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Also used to hide a cross-tenant answer from a host (404, not 403 — no existence leak). */
public class AnswerNotFoundException extends DomainException {

    public AnswerNotFoundException() {
        super(HttpStatus.NOT_FOUND, ScoringConstants.ANSWER_NOT_FOUND);
    }
}
