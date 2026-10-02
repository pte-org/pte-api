package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A different retry or a score write after publication is rejected without mutating the saved score. */
public class ExaminerScoreConflictException extends DomainException {

    public ExaminerScoreConflictException() {
        super(HttpStatus.CONFLICT, ExaminerScoringConstants.SCORE_CONFLICT);
    }
}
