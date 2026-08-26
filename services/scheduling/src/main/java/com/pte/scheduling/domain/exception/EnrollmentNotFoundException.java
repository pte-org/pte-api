package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class EnrollmentNotFoundException extends DomainException {

    public EnrollmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, SchedulingConstants.ENROLLMENT_NOT_FOUND);
    }
}
