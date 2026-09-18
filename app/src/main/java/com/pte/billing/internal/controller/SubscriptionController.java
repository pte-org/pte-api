package com.pte.billing.internal.controller;

import com.pte.billing.BillingService;
import com.pte.billing.internal.dto.response.SubscriptionResponse;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant read endpoint for currently usable purchased exam packages. */
@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class SubscriptionController {

    private final BillingService billingService;

    public SubscriptionController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping
    public ApiResponse<List<SubscriptionResponse>> list() {
        CurrentUser caller = CurrentUserContext.required();
        if (caller.tenantId() == null) {
            return ApiResponse.success(List.of());
        }
        return ApiResponse.success(billingService.listActiveSubscriptions(caller.tenantId()).stream()
                .map(SubscriptionResponse::from)
                .toList());
    }
}
