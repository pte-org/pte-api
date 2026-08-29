package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** {@code playCount} already reached the pinned (item-override or session-policy) replay limit for this task. */
public class ReplayLimitExceededException extends DomainException {

    public ReplayLimitExceededException() {
        super(HttpStatus.FORBIDDEN, ExamDeliveryConstants.REPLAY_LIMIT_EXCEEDED);
    }
}
