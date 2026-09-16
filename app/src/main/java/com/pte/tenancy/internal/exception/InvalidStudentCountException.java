package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** Raised when a student-capacity operation has no positive number of students. */
public class InvalidStudentCountException extends DomainException {

    public InvalidStudentCountException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, TenancyConstants.STUDENT_COUNT_INVALID);
    }
}
