package com.pte.practice.internal.exception;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Protected practice mutation/content access failed the live entitlement check. */
public class PracticeNotEntitledException extends DomainException {

    public PracticeNotEntitledException() {
        super(HttpStatus.FORBIDDEN, PracticeConstants.PRACTICE_NOT_ENTITLED);
    }
}
