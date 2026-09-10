package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class LecturerAssignmentNotFoundException extends DomainException {

    public LecturerAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.LECTURER_ASSIGNMENT_NOT_FOUND);
    }
}
