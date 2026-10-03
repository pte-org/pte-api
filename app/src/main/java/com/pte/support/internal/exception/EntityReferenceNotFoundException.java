package com.pte.support.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.support.internal.constant.SupportConstants;
import org.springframework.http.HttpStatus;

public class EntityReferenceNotFoundException extends DomainException {

    public EntityReferenceNotFoundException() {
        super(HttpStatus.NOT_FOUND, SupportConstants.ENTITY_REFERENCE_NOT_FOUND,
                null, SupportConstants.ENTITY_REFERENCE_NOT_FOUND_FRIENDLY);
    }
}
