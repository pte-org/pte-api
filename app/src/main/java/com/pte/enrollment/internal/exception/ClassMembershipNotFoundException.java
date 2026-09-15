package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ClassMembershipNotFoundException extends DomainException {

    public ClassMembershipNotFoundException() {
        super(HttpStatus.NOT_FOUND, EnrollmentConstants.CLASS_MEMBERSHIP_NOT_FOUND);
    }
}
