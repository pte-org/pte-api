package com.pte.authoring.domain.exception;

import com.pte.authoring.constant.AuthoringConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class BlueprintNotFoundException extends DomainException {

    public BlueprintNotFoundException() {
        super(HttpStatus.NOT_FOUND, AuthoringConstants.BLUEPRINT_NOT_FOUND);
    }
}
