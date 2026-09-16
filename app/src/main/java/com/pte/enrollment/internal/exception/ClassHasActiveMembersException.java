package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ClassHasActiveMembersException extends DomainException {

    public ClassHasActiveMembersException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.CLASS_HAS_ACTIVE_MEMBERS);
    }
}
