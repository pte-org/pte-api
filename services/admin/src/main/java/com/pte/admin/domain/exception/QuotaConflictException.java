package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a concurrent grant raced the {@code @Version} check on {@code Tenant}. */
public class QuotaConflictException extends DomainException {

    public QuotaConflictException() {
        super(HttpStatus.CONFLICT, AdminConstants.QUOTA_CONFLICT);
    }
}
