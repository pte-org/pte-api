package com.pte.tenancy.internal.exception;

import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Thrown when a concurrent grant raced the {@code @Version} check on {@code Tenant}. */
public class QuotaConflictException extends DomainException {

    public QuotaConflictException() {
        super(HttpStatus.CONFLICT, TenancyConstants.QUOTA_CONFLICT);
    }
}
