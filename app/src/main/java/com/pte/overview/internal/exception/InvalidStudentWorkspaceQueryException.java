package com.pte.overview.internal.exception;

import com.pte.overview.internal.constant.StudentWorkspaceConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Rejects values outside the public student workspace query contract. */
public class InvalidStudentWorkspaceQueryException extends DomainException {

    public InvalidStudentWorkspaceQueryException() {
        super(HttpStatus.BAD_REQUEST, StudentWorkspaceConstants.INVALID_QUERY);
    }
}
