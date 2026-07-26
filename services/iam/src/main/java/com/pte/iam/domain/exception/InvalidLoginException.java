package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/** Wrong email/password, or a suspended account attempting to log in. */
public class InvalidLoginException extends DomainException {

    public InvalidLoginException() {
        super(HttpStatus.UNAUTHORIZED, IamConstants.INVALID_LOGIN);
    }
}
