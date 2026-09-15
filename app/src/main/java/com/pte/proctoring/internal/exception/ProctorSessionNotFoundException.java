package com.pte.proctoring.internal.exception;

import com.pte.proctoring.internal.constant.ProctorConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProctorSessionNotFoundException extends DomainException {

    public ProctorSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProctorConstants.PROCTOR_SESSION_NOT_FOUND);
    }
}
