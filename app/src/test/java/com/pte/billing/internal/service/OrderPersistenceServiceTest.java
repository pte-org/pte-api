package com.pte.billing.internal.service;

import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.repository.OrderRepository;
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

    @BeforeEach
    void setUp() {
        service = new OrderPersistenceService(orderRepository, eventPublisher);
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
}
