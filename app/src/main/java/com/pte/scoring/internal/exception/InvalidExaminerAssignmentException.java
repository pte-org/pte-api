package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerAssignmentConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidExaminerAssignmentException extends DomainException {

    public InvalidExaminerAssignmentException(String reason) {
        super(HttpStatus.BAD_REQUEST, ExaminerAssignmentConstants.INVALID_ASSIGNMENT, null,
                ExaminerAssignmentConstants.INVALID_ASSIGNMENT_MESSAGE, reason);
    }
}
