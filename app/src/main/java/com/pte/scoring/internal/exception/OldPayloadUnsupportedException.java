package com.pte.scoring.internal.exception;

import com.pte.scoring.internal.constant.ExaminerScoringConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a mutation attempt uses the old score-only payload missing modern decision metadata. */
public class OldPayloadUnsupportedException extends DomainException {

    public OldPayloadUnsupportedException() {
        super(HttpStatus.BAD_REQUEST, ExaminerScoringConstants.OLD_PAYLOAD_UNSUPPORTED);
    }
}
