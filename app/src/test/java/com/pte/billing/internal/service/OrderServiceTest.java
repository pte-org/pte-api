package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.OrderResponse;
import com.pte.billing.internal.exception.OrderException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private PayOsClient payOsClient;

    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(orderRepository, planRepository, orderPersistenceService, payOsClient);
    }

    @Test
    void createOrder_reservesPlanPriceAndReturnsPaymentLink() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Plan plan = activePlan(planId);
        Order reserved = Order.pending(tenantId, planId, 1_000_000_000L,
                plan.getPrice(), plan.getCurrency());

        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));
        when(orderRepository.existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(
                tenantId, planId, OrderStatus.PENDING)).thenReturn(false);
        when(orderRepository.nextOrderCode()).thenReturn(1_000_000_000L);
        when(orderPersistenceService.reserve(any(Order.class))).thenReturn(reserved);
        when(payOsClient.createPaymentLink(reserved)).thenReturn("https://pay.payos.vn/web/test");
        when(orderPersistenceService.attachPaymentLink(any(), eq("https://pay.payos.vn/web/test")))
                .thenAnswer(invocation -> {
                    reserved.attachPaymentLink(invocation.getArgument(1));
                    return reserved;
                });

        OrderResponse response = service.createOrder(tenantId, planId);

        assertThat(response.amount()).isEqualByComparingTo("50000");
        assertThat(response.currency()).isEqualTo("VND");
        assertThat(response.paymentLinkUrl()).isEqualTo("https://pay.payos.vn/web/test");
        verify(orderPersistenceService).reserve(any(Order.class));
        verify(payOsClient).createPaymentLink(reserved);
    }

    @Test
    void createOrder_rejectsSecondPendingOrderForSameTenantAndPlan() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(activePlan(planId)));
        when(orderRepository.existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(
                tenantId, planId, OrderStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.createOrder(tenantId, planId))
                .isInstanceOfSatisfying(OrderException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.ORDER_PENDING_EXISTS);
                });
        verify(orderRepository, never()).nextOrderCode();
        verify(orderPersistenceService, never()).reserve(any());
        verifyNoPayOsCalls();
    }

    @Test
    void createOrder_rejectsInactivePlanBeforeCreatingOrder() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Plan plan = activePlan(planId);
        plan.setStatus(PlanStatus.ARCHIVED);
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));

        assertThatThrownBy(() -> service.createOrder(tenantId, planId))
                .isInstanceOfSatisfying(OrderException.class, ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(ex.getMessage()).isEqualTo(BillingConstants.ORDER_PLAN_NOT_ACTIVE);
                });
        verify(orderRepository, never()).existsByTenantIdAndPlanIdAndStatusAndDeletedFalse(any(), any(), any());
        verifyNoPayOsCalls();
    }

    private Plan activePlan(UUID publicId) {
        Plan plan = new Plan();
        plan.setPublicId(publicId);
        plan.setName("Exam");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(new BigDecimal("50000.00"));
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(25);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }

    private void verifyNoPayOsCalls() {
        verify(payOsClient, never()).createPaymentLink(any());
    }
}
