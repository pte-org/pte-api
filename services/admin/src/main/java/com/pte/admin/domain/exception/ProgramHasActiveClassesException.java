package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ProgramHasActiveClassesException extends DomainException {

    public ProgramHasActiveClassesException() {
        super(HttpStatus.CONFLICT, AdminConstants.PROGRAM_HAS_ACTIVE_CLASSES);
    }
}
