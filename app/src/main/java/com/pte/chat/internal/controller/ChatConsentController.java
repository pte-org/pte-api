package com.pte.chat.internal.controller;

import com.pte.chat.internal.constant.ChatConstants;
import com.pte.chat.internal.dto.request.ChatConsentRequest;
import com.pte.chat.internal.dto.response.ChatConsentResponse;
import com.pte.chat.internal.service.consent.ChatConsentService;
import com.pte.chat.internal.service.consent.ChatConsentStatus;
import com.pte.practice.PracticeEntitlementGate;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ChatConstants.CHAT_PATH + ChatConstants.CONSENT_PATH)
@PreAuthorize("hasRole('STUDENT')")
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
public class ChatConsentController {

    private final ChatConsentService consentService;
    private final PracticeEntitlementGate entitlementGate;

    public ChatConsentController(ChatConsentService consentService, PracticeEntitlementGate entitlementGate) {
        this.consentService = consentService;
        this.entitlementGate = entitlementGate;
    }

    @GetMapping
    public ApiResponse<ChatConsentResponse> status() {
        CurrentUser caller = CurrentUserContext.required();
        entitlementGate.assertUnlocked(caller, null);
        return ApiResponse.success(response(consentService.status(caller.userId())));
    }

    @PostMapping
    public ApiResponse<ChatConsentResponse> accept(@Valid @RequestBody ChatConsentRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        entitlementGate.assertUnlocked(caller, null);
        return ApiResponse.success(response(
                consentService.give(caller.userId(), request.policyVersion(), request.ageConfirmed())));
    }

    /** Withdraws the caller's consent. No entitlement check: a lapsed student may still withdraw. */
    @DeleteMapping
    public ApiResponse<ChatConsentResponse> withdraw() {
        return ApiResponse.success(response(consentService.withdraw(CurrentUserContext.required().userId())));
    }

    private static ChatConsentResponse response(ChatConsentStatus status) {
        return new ChatConsentResponse(status.policyVersion(), status.consented(), status.consentedAt());
    }
}
