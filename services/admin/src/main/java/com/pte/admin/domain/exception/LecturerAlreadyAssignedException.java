package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class LecturerAlreadyAssignedException extends DomainException {

    public LecturerAlreadyAssignedException() {
        super(HttpStatus.CONFLICT, AdminConstants.LECTURER_ALREADY_ASSIGNED);
    }
}
