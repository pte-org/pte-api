package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Missing, cross-tenant, inactive, or unassigned Examiner work shares one 404 response. */
public class ExaminerWorkNotFoundException extends DomainException {

    public ExaminerWorkNotFoundException() {
        super(HttpStatus.NOT_FOUND, ExaminerScoringConstants.WORK_NOT_FOUND);
    }
}
