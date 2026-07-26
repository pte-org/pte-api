package com.pte.proctor.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.proctor.constant.ProctorConstants;
import org.springframework.http.HttpStatus;

public class ProctorSessionNotFoundException extends DomainException {

    public ProctorSessionNotFoundException() {
        super(HttpStatus.NOT_FOUND, ProctorConstants.PROCTOR_SESSION_NOT_FOUND);
    }
}
