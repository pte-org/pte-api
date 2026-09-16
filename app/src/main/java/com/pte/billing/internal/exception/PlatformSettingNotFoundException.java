package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Missing platform configuration is explicit and never replaced by a default. */
public class PlatformSettingNotFoundException extends DomainException {

    public PlatformSettingNotFoundException(String key) {
        super(HttpStatus.INTERNAL_SERVER_ERROR, BillingConstants.PLATFORM_SETTING_NOT_FOUND);
    }
}
