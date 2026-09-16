package com.pte.itembank.internal.service;

import com.pte.shared.security.CurrentUser;
import org.springframework.stereotype.Component;

/** Platform question-bank write policy after the private bank is retired. */
@Component
public class ItembankAccessPolicy {

    public boolean canWrite(CurrentUser caller) {
        return caller.isPlatformUser();
    }
}
