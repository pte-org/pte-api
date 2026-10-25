package com.pte.billing.internal.service.order;

import com.pte.billing.internal.service.order.OrderPersistenceService;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderPersistenceServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private OrderPersistenceService service;

    @Mock private ServicePlanRepository planRepository;

    @Mock private SubscriptionActivationService subscriptionActivationService;

    @BeforeEach
    void setUp() {
        service = new OrderPersistenceService(orderRepository, eventPublisher, planRepository, null, com.pte.TestClocks.fixed());
    }

    @Test
    void expireIfPendingLocksExactOrderAndPublishesWithinTheTransition() {
        UUID publicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, 1_000_000_006L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(publicId);
        when(orderRepository.findByPublicIdForUpdateAndDeletedFalse(publicId)).thenReturn(Optional.of(order));

        assertThat(service.expireIfPending(publicId)).isTrue();

        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        verify(orderRepository).saveAndFlush(order);
        ArgumentCaptor<OrderExpiredEvent> captor = ArgumentCaptor.forClass(OrderExpiredEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantPublicId()).isEqualTo(tenantId);
        assertThat(captor.getValue().orderPublicId()).isEqualTo(publicId);
        assertThat(captor.getValue().planPublicId()).isEqualTo(planId);
    }

    @Test
    void expireIfPendingDoesNotPublishWhenPaymentAlreadyWonTheLock() {
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_007L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(UUID.randomUUID());
        order.markPaid(java.time.Instant.now());
        when(orderRepository.findByPublicIdForUpdateAndDeletedFalse(order.getPublicId()))
                .thenReturn(Optional.of(order));

        assertThat(service.expireIfPending(order.getPublicId())).isFalse();

        verify(orderRepository, never()).saveAndFlush(any(Order.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void completeFreeOrderActivatesSubscriptionAndPublishesCommercialOutcome() {
        UUID publicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, 1_000_000_008L,
                BigDecimal.ZERO, "VND");
        order.setPublicId(publicId);
        ServicePlan plan = new ServicePlan();
        plan.setPublicId(planId);
        plan.setStatus(PlanStatus.ACTIVE);
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(1);
        UUID subscriptionId = UUID.randomUUID();
        SubscriptionActivationResponse activation = SubscriptionActivationResponse.fromSubscription(
                tenantId, planId, "PTE-TEST", java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(60), 1, "ACTIVE", "PAYMENT", subscriptionId);
        service = new OrderPersistenceService(orderRepository, eventPublisher, planRepository,
                subscriptionActivationService, com.pte.TestClocks.fixed());
        when(orderRepository.findByPublicIdForUpdateAndDeletedFalse(publicId)).thenReturn(Optional.of(order));
        when(planRepository.findByPublicIdForUpdate(planId)).thenReturn(Optional.of(plan));
        when(subscriptionActivationService.activate(tenantId, plan,
                com.pte.billing.domain.enums.ActivationSource.PAYMENT)).thenReturn(activation);
        when(orderRepository.saveAndFlush(order)).thenReturn(order);

        Order result = service.completeFreeOrder(publicId);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(result.getPaidAt()).isNotNull();
        verify(orderRepository).saveAndFlush(order);
        verify(eventPublisher).publishEvent(any(CommercialOutcomeConfirmedEvent.class));
    }
}
