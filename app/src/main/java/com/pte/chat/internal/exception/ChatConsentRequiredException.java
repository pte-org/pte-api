package com.pte.chat.internal.exception;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

import java.util.Map;

/** Chat is closed until the student has accepted the current privacy notice; the body names its version. */
public class ChatConsentRequiredException extends DomainException {

    public ChatConsentRequiredException() {
        super(HttpStatus.FORBIDDEN, ChatConstants.ERR_CONSENT_REQUIRED,
                Map.of(ChatConstants.DATA_POLICY_VERSION, ChatConstants.CONSENT_POLICY_VERSION),
                ChatConstants.CONSENT_REQUIRED_MESSAGE);
    }
}
