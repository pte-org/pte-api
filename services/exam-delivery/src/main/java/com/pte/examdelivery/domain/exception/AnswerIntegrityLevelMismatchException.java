package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** Submission request shape (plain vs. encrypted) doesn't match the attempt's pinned {@code answerIntegrityLevel} — server-decided routing, never client-chosen. */
public class AnswerIntegrityLevelMismatchException extends DomainException {

    public AnswerIntegrityLevelMismatchException() {
        super(HttpStatus.CONFLICT, ExamDeliveryConstants.ANSWER_INTEGRITY_LEVEL_MISMATCH);
    }
}
