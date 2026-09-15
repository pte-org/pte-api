package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Rejects values outside the public student-roster query contract. */
public class InvalidStudentRosterQueryException extends DomainException {

    public InvalidStudentRosterQueryException() {
        super(HttpStatus.BAD_REQUEST, AdminConstants.INVALID_STUDENT_ROSTER_QUERY);
    }
}
