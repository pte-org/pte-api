package com.pte.session.internal.exception;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A student with an attempt in the exam keeps their roster row so the attempt and its scores stay reachable. */
public class EnrolledStudentAlreadyStartedException extends DomainException {

    public EnrolledStudentAlreadyStartedException() {
        super(HttpStatus.CONFLICT, SessionConstants.ENROLLED_STUDENT_ALREADY_STARTED, null,
                SessionConstants.ENROLLED_STUDENT_ALREADY_STARTED_FRIENDLY);
    }
}
