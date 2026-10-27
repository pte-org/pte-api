package com.pte.scoring.internal.exception.examiner;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a request public id has already been used with different intent or on another answer. */
public class ExaminerReceiptConflictException extends DomainException {

    public ExaminerReceiptConflictException() {
        super(HttpStatus.CONFLICT, ExaminerScoringConstants.RECEIPT_CONFLICT);
    }
}
