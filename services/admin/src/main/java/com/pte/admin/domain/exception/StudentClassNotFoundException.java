package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class StudentClassNotFoundException extends DomainException {

    public StudentClassNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.CLASS_NOT_FOUND);
    }
}
