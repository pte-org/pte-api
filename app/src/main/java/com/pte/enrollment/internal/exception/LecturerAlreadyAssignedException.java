package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class LecturerAlreadyAssignedException extends DomainException {

    public LecturerAlreadyAssignedException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.LECTURER_ALREADY_ASSIGNED);
    }
}
