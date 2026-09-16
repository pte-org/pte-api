package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A stored platform setting cannot be converted to the type required by a caller. */
public class PlatformSettingInvalidException extends DomainException {

    public PlatformSettingInvalidException(String key) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, BillingConstants.PLATFORM_SETTING_INVALID);
    }
}
