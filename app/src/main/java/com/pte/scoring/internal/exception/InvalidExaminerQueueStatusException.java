package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidExaminerQueueStatusException extends DomainException {

    public InvalidExaminerQueueStatusException() {
        super(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.INVALID_QUEUE_STATUS);
    }
}
