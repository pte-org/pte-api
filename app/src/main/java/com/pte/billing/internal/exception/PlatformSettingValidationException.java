package com.pte.billing.internal.exception;

import com.pte.billing.internal.constant.BillingConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** A platform administrator supplied a value outside the setting contract. */
public class PlatformSettingValidationException extends DomainException {

    public PlatformSettingValidationException(String code) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code == null
                ? BillingConstants.PLATFORM_SETTING_VALUE_INVALID
                : code);
    }
}
