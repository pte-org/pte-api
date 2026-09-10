package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CoordinatorAssignmentNotFoundException extends DomainException {

    public CoordinatorAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.COORDINATOR_ASSIGNMENT_NOT_FOUND);
    }
}
