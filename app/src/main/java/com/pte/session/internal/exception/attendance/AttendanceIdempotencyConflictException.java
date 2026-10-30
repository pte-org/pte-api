package com.pte.session.internal.exception.attendance;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttendanceIdempotencyConflictException extends DomainException {
    public AttendanceIdempotencyConflictException(String message) {
        super(HttpStatus.CONFLICT, "ATTENDANCE_IDEMPOTENCY_CONFLICT", null, message);
    }
}
