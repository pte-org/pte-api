package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StudentClassNameAlreadyUsedException extends DomainException {

    public StudentClassNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, AdminConstants.CLASS_NAME_ALREADY_USED);
    }
}
