package com.pte.chat.internal.dto.request;

import com.pte.chat.internal.constant.ChatConstants;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ChatConsentRequest(
        @NotBlank(message = ChatConstants.POLICY_VERSION_REQUIRED_MESSAGE) String policyVersion,
        @NotNull(message = ChatConstants.AGE_CONFIRMED_REQUIRED_MESSAGE) Boolean ageConfirmed) {
}
