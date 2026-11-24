package com.pte.session.internal.exception.enrollment;

import com.pte.session.internal.constant.SessionConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** The session has proctors, and none of them has checked this student in yet. */
public class AttendanceRequiredException extends DomainException {

    public AttendanceRequiredException() {
        super(HttpStatus.FORBIDDEN, SessionConstants.ATTENDANCE_REQUIRED, null,
                SessionConstants.ATTENDANCE_REQUIRED_FRIENDLY);
    }
}
