package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ClassMembershipNotFoundException extends DomainException {

    public ClassMembershipNotFoundException() {
        super(HttpStatus.NOT_FOUND, AdminConstants.CLASS_MEMBERSHIP_NOT_FOUND);
    }
}
