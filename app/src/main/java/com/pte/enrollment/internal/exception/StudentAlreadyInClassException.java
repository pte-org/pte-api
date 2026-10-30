package com.pte.enrollment.internal.exception;

import com.pte.enrollment.internal.constant.EnrollmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

public class StudentAlreadyInClassException extends DomainException {

    public StudentAlreadyInClassException() {
        super(HttpStatus.CONFLICT, EnrollmentConstants.STUDENT_ALREADY_IN_CLASS, null,
                EnrollmentConstants.STUDENT_ALREADY_IN_CLASS_FRIENDLY);
    }

    public StudentAlreadyInClassException(UUID existingClassPublicId) {
        super(HttpStatus.CONFLICT, EnrollmentConstants.STUDENT_ALREADY_IN_CLASS,
                Map.of(EnrollmentConstants.DATA_EXISTING_CLASS_PUBLIC_ID, existingClassPublicId),
                EnrollmentConstants.STUDENT_ALREADY_IN_CLASS_FRIENDLY);
    }
}
