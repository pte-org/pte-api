package com.pte.proctor.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.proctor.constant.ProctorConstants;
import org.springframework.http.HttpStatus;

/** scheduling's assignment-check call failed at the infra level (timeout/5xx/circuit open) — distinct from a clean denial. */
public class ProctorAssignmentCheckFailedException extends DomainException {

    public ProctorAssignmentCheckFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ProctorConstants.PROCTOR_ASSIGNMENT_CHECK_FAILED);
    }
}
