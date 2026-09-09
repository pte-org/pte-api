package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StudentAlreadyInClassException extends DomainException {

    public StudentAlreadyInClassException() {
        super(HttpStatus.CONFLICT, AdminConstants.STUDENT_ALREADY_IN_CLASS);
    }
}
