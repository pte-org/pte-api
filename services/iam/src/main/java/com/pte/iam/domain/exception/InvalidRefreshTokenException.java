package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends DomainException {

    public InvalidRefreshTokenException() {
        super(HttpStatus.UNAUTHORIZED, IamConstants.INVALID_REFRESH_TOKEN);
    }
}
