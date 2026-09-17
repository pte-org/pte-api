package com.pte.billing.internal.controller;

import com.pte.billing.internal.dto.request.CreateOrderRequest;
import com.pte.billing.internal.dto.response.OrderResponse;
import com.pte.billing.internal.service.OrderService;
import com.pte.shared.security.CurrentUser;
import com.pte.shared.security.CurrentUserContext;
import com.pte.shared.web.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Tenant-owned PayOS order creation and history endpoints. */
@RestController
@RequestMapping("/orders")
@PreAuthorize("hasAnyRole('HOST_ADMIN','HOST_AUTHOR')")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        CurrentUser caller = CurrentUserContext.required();
        OrderResponse response = orderService.createOrder(caller.tenantId(), request.planId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> list() {
        CurrentUser caller = CurrentUserContext.required();
        return ApiResponse.success(orderService.listOrders(caller.tenantId()));
    }
}
