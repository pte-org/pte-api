package com.pte.itembank.internal.exception;

import com.pte.itembank.internal.constant.ItembankConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A non-platform caller attempted to write SHARED (platform-bank) content. */
public class SharedWriteForbiddenException extends DomainException {

    public SharedWriteForbiddenException() {
        super(HttpStatus.FORBIDDEN, ItembankConstants.SHARED_WRITE_FORBIDDEN);
    }
}
