package com.pte.billing.internal.controller;

import com.pte.billing.BillingService;
import com.pte.billing.internal.dto.response.OrderResponse;
import com.pte.billing.internal.dto.response.PlatformOrderResponse;
import com.pte.billing.internal.dto.response.PlatformSubscriptionResponse;
import com.pte.billing.internal.service.OrderService;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import com.pte.shared.web.PagedResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Read-only platform-wide commercial projections for admin and operations manager. */
@RestController
@RequestMapping("/api/v1/platform/commercial")
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','PLATFORM_MANAGER')")
public class PlatformCommercialController {

    private final OrderService orderService;
    private final BillingService billingService;

    public PlatformCommercialController(OrderService orderService, BillingService billingService) {
        this.orderService = orderService;
        this.billingService = billingService;
    }

    @GetMapping("/orders")
    public ApiResponse<PagedResult<PlatformOrderResponse>> orders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID tenantId) {
        PagedResult<OrderResponse> result = orderService.listPlatformOrders(page, size, tenantId,
                CurrentUserContext.required());
        return ApiResponse.success(new PagedResult<>(result.data().stream().map(PlatformOrderResponse::from).toList(),
                result.meta()));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<PagedResult<PlatformSubscriptionResponse>> subscriptions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID tenantId) {
        PagedResult<com.pte.billing.SubscriptionView> result = billingService.listPlatformSubscriptions(
                CurrentUserContext.required(), page, size, tenantId);
        return ApiResponse.success(new PagedResult<>(result.data().stream().map(PlatformSubscriptionResponse::from).toList(),
                result.meta()));
    }
}
