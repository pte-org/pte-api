package com.pte.iam.domain.exception;

import com.pte.common.exception.DomainException;
import com.pte.iam.constant.IamConstants;
import org.springframework.http.HttpStatus;

/** A tenant-scoped caller attempted to act on a user outside its own tenant. */
public class CrossTenantAccessException extends DomainException {

    public CrossTenantAccessException() {
        super(HttpStatus.FORBIDDEN, IamConstants.CROSS_TENANT_ACCESS);
    }
}
