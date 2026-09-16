package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.OrderResponse;
import com.pte.billing.internal.exception.OrderException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Creates PayOS orders and exposes only the current tenant's order history. */
@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final PlanRepository planRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final PayOsClient payOsClient;

    public OrderService(OrderRepository orderRepository, PlanRepository planRepository,
            OrderPersistenceService orderPersistenceService, PayOsClient payOsClient) {
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.payOsClient = payOsClient;
    }

    public OrderResponse createOrder(UUID tenantId, UUID planPublicId) {
        if (tenantId == null) {
            throw invalid(BillingConstants.ORDER_TENANT_REQUIRED);
        }
        if (planPublicId == null) {
            throw invalid(BillingConstants.ORDER_PLAN_REQUIRED);
        }

        Plan plan = planRepository.findByPublicId(planPublicId)
                .orElseThrow(PlanNotFoundException::new);
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PLAN_NOT_ACTIVE);
        }
        validatePayOsAmount(plan.getPrice(), plan.getCurrency());
        if (orderRepository.existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(
                tenantId, planPublicId, OrderStatus.PENDING)) {
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PENDING_EXISTS);
        }

        Order order = Order.pending(tenantId, planPublicId, orderRepository.nextOrderCode(),
                plan.getPrice(), plan.getCurrency().toUpperCase(Locale.ROOT));
        try {
            order = orderPersistenceService.reserve(order);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index is the final race-safe guard when two
            // browser requests pass the read-side pending check together.
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PENDING_EXISTS);
        }

        String paymentLinkUrl = payOsClient.createPaymentLink(order);
        return OrderResponse.from(orderPersistenceService.attachPaymentLink(
                order.getPublicId(), paymentLinkUrl));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders(UUID tenantId) {
        if (tenantId == null) {
            throw invalid(BillingConstants.ORDER_TENANT_REQUIRED);
        }
        return orderRepository.findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(tenantId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    private void validatePayOsAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) {
            throw invalid(BillingConstants.ORDER_AMOUNT_INVALID);
        }
        try {
            amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw invalid(BillingConstants.ORDER_AMOUNT_INVALID);
        }
        if (currency == null || !BillingConstants.PAYOS_CURRENCY.equalsIgnoreCase(currency)) {
            throw invalid(BillingConstants.ORDER_CURRENCY_UNSUPPORTED);
        }
    }

    private OrderException invalid(String code) {
        return new OrderException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }
}
