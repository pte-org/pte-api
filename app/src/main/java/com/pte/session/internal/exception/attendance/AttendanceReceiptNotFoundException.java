package com.pte.session.internal.exception.attendance;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AttendanceReceiptNotFoundException extends DomainException {
    public AttendanceReceiptNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RECEIPT_NOT_FOUND", null, message);
    }
}
