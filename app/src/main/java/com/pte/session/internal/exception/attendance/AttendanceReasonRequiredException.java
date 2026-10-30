package com.pte.session.internal.exception.attendance;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttendanceReasonRequiredException extends DomainException {
    public AttendanceReasonRequiredException(String message) {
        super(HttpStatus.BAD_REQUEST, "ATTENDANCE_REASON_REQUIRED", null, message);
    }
}
