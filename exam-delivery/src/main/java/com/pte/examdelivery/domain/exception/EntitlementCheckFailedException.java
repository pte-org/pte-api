package com.pte.examdelivery.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import org.springframework.http.HttpStatus;

/** The guarded call to scheduling failed (timeout, circuit open) — distinct from a clean NOT_ENTITLED denial. */
public class EntitlementCheckFailedException extends DomainException {

    public EntitlementCheckFailedException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, ExamDeliveryConstants.ENTITLEMENT_CHECK_FAILED);
    }
}
