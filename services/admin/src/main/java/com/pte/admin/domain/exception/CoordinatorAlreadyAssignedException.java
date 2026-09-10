package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class CoordinatorAlreadyAssignedException extends DomainException {

    public CoordinatorAlreadyAssignedException() {
        super(HttpStatus.CONFLICT, AdminConstants.COORDINATOR_ALREADY_ASSIGNED);
    }
}
