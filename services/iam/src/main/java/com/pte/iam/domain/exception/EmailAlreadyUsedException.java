package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

public class EmailAlreadyUsedException extends DomainException {

    public EmailAlreadyUsedException() {
        super(HttpStatus.CONFLICT, IamConstants.EMAIL_ALREADY_USED);
    }
}
