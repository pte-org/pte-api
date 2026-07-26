package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends DomainException {

    public UserNotFoundException() {
        super(HttpStatus.NOT_FOUND, IamConstants.USER_NOT_FOUND);
    }
}
