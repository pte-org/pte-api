package com.pte.session.internal.exception;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttendanceConflictException extends DomainException {
    public AttendanceConflictException(String message) {
        super(HttpStatus.CONFLICT, "ATTENDANCE_VERSION_CONFLICT", null, message);
    }
}
