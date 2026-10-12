package com.pte.examstaff.internal;

import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

final class StaffWorkspaceException extends DomainException {
    StaffWorkspaceException(HttpStatus status, String code) { super(status, code); }
    static StaffWorkspaceException invalidQuery() {
        return new StaffWorkspaceException(HttpStatus.BAD_REQUEST, StaffWorkspaceConstants.INVALID_QUERY);
    }
}
