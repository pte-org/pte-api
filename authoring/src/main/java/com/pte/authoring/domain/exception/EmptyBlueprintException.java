package com.pte.authoring.domain.exception;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Attempted to publish a blueprint with no items. */
public class EmptyBlueprintException extends DomainException {

    public EmptyBlueprintException() {
        super(HttpStatus.BAD_REQUEST, AuthoringConstants.EMPTY_BLUEPRINT);
    }
}
