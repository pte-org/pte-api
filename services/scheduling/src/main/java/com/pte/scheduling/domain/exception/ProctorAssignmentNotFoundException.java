package com.pte.scheduling.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.scheduling.constant.SchedulingConstants;
import org.springframework.http.HttpStatus;

public class ProctorAssignmentNotFoundException extends DomainException {

    public ProctorAssignmentNotFoundException() {
        super(HttpStatus.NOT_FOUND, SchedulingConstants.PROCTOR_ASSIGNMENT_NOT_FOUND);
    }
}
