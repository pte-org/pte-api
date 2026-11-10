package com.pte.scoring.internal.exception.examiner;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidExaminerQueueDateException extends DomainException {

    public InvalidExaminerQueueDateException() {
        super(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.INVALID_QUEUE_DATE);
    }
}
