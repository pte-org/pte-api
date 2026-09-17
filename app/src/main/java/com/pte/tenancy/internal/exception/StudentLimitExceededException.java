package com.pte.tenancy.internal.exception;

import com.pte.shared.exception.DomainException;
import com.pte.tenancy.internal.constant.TenancyConstants;
import org.springframework.http.HttpStatus;

/** Raised when a student create/import would exceed the tenant's capacity. */
public class StudentLimitExceededException extends DomainException {

    private final long current;
    private final long limit;
    private final long adding;

    public StudentLimitExceededException(long current, long limit, long adding) {
        super(HttpStatus.CONFLICT, String.format(TenancyConstants.STUDENT_LIMIT_EXCEEDED,
                current, limit, adding));
        this.current = current;
        this.limit = limit;
        this.adding = adding;
    }

    public long getCurrent() {
        return current;
    }

    public long getLimit() {
        return limit;
    }

    public long getAdding() {
        return adding;
    }
}
