package com.pte.session.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttendanceWindowClosedException extends DomainException {
    public AttendanceWindowClosedException(String message) {
        super(HttpStatus.FORBIDDEN, "ATTENDANCE_WINDOW_CLOSED", null, message);
    }
}
