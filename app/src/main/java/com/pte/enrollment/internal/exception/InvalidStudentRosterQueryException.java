package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Rejects values outside the public student-roster query contract. */
public class InvalidStudentRosterQueryException extends DomainException {

    public InvalidStudentRosterQueryException() {
        super(HttpStatus.BAD_REQUEST, EnrollmentConstants.INVALID_STUDENT_ROSTER_QUERY);
    }
}
