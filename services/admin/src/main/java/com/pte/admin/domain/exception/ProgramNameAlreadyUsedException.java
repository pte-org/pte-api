package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramNameAlreadyUsedException extends DomainException {

    public ProgramNameAlreadyUsedException() {
        super(HttpStatus.CONFLICT, AdminConstants.PROGRAM_NAME_ALREADY_USED);
    }
}
