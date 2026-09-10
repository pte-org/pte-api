package com.pte.admin.domain.exception;

import com.pte.admin.constant.AdminConstants;
import com.pte.common.exception.DomainException;
import org.springframework.http.HttpStatus;

public class ClassHasActiveMembersException extends DomainException {

    public ClassHasActiveMembersException() {
        super(HttpStatus.CONFLICT, AdminConstants.CLASS_HAS_ACTIVE_MEMBERS);
    }
}
