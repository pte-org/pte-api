package com.pte.scoring.internal.exception.examiner;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when finalization is attempted before the session's MANUAL_EXAMINER cohort is frozen. */
public class ExaminerCohortRequiredException extends DomainException {

    public ExaminerCohortRequiredException(String reasonCode) {
        super(HttpStatus.CONFLICT, reasonCode != null ? reasonCode : ExaminerScoringConstants.MISSING_MANUAL_COHORT);
    }

    public ExaminerCohortRequiredException() {
        this(ExaminerScoringConstants.MISSING_MANUAL_COHORT);
    }
}
