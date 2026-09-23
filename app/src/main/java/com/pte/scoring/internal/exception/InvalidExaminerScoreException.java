package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidExaminerScoreException extends DomainException {

    public InvalidExaminerScoreException() {
        super(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.INVALID_SCORE);
    }
}
