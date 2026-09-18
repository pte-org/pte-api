package com.pte.assessment.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BlueprintVersionConflictException extends DomainException {

    public BlueprintVersionConflictException() {
        super(HttpStatus.CONFLICT, "Blueprint was changed by another user");
    }
}
