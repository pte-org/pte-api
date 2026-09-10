package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramNotFoundException extends DomainException {

    public ProgramNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.PROGRAM_NOT_FOUND);
    }
}
