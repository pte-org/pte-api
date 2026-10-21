package com.pte.overview.internal.exception;

import com.pte.overview.internal.constant.StudentWorkspaceConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Deliberately does not distinguish a missing student from a foreign student. */
public class StudentWorkspaceNotFoundException extends DomainException {

    public StudentWorkspaceNotFoundException() {
        super(HttpStatus.NOT_FOUND, StudentWorkspaceConstants.STUDENT_NOT_FOUND);
    }
}
