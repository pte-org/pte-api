package com.pte.scoring.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ScoreSourceSelectionException extends DomainException {

    public ScoreSourceSelectionException(HttpStatus status, String code, Object data, String message) {
        super(status, code, data, message, message);
    }
}
