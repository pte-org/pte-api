package com.pte.billing.internal.service.order;

import com.pte.billing.internal.service.order.OrderExpirationService;
import com.pte.billing.internal.service.order.OrderPersistenceService;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.order.PayOsException;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderExpirationServiceTest {

    @Mock
    private com.pte.billing.internal.repository.OrderRepository orderRepository;

    @Mock
    private OrderPersistenceService orderPersistenceService;

    @Mock
    private PayOsClient payOsClient;

    private OrderExpirationService service;

    @BeforeEach
    void setUp() {
        service = new OrderExpirationService(orderRepository, orderPersistenceService, payOsClient, 24, com.pte.TestClocks.fixed());
    }

    @Test
    void expireDueOrders_cancelsPayOsLinkBeforeExpiringPendingOrder() {
        UUID publicId = UUID.randomUUID();
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_002L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(publicId);
        order.attachPaymentLink("https://pay.payos.vn/web/test");
        order.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        when(orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                any(OrderStatus.class), any(Instant.class))).thenReturn(List.of(order));
        when(orderPersistenceService.expireIfPending(publicId)).thenReturn(true);

        int expired = service.expireDueOrders();

        assertThat(expired).isEqualTo(1);
        verify(payOsClient).cancelPaymentLink(order.getOrderCode());
        verify(orderPersistenceService).expireIfPending(publicId);
    }

    @Test
    void expireDueOrdersKeepsPendingWhenProviderCancellationFailsAndEmitsNothing() {
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_005L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(UUID.randomUUID());
        order.attachPaymentLink("https://pay.payos.vn/web/test");
        order.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        when(orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                any(OrderStatus.class), any(Instant.class))).thenReturn(List.of(order));
        org.mockito.Mockito.doThrow(new PayOsException(BillingConstants.PAYOS_CANCELLATION_FAILED))
                .when(payOsClient).cancelPaymentLink(order.getOrderCode());

        assertThat(service.expireDueOrders()).isZero();

        verify(orderPersistenceService, never()).expireIfPending(order.getPublicId());
    }

    @Test
    void expireDueOrdersExpiresAnOrderThatNeverGotAPaymentLinkWithoutCallingPayOs() {
        UUID publicId = UUID.randomUUID();
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_006L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(publicId);
        order.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        when(orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                any(OrderStatus.class), any(Instant.class))).thenReturn(List.of(order));
        when(orderPersistenceService.expireIfPending(publicId)).thenReturn(true);

        assertThat(service.expireDueOrders()).isEqualTo(1);

        verify(payOsClient, never()).cancelPaymentLink(any());
        verify(orderPersistenceService).expireIfPending(publicId);
    }

    @Test
    void expireDueOrdersKeepsGoingWhenOneOrderFailsWithAnUnexpectedError() {
        Order broken = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_007L,
                new BigDecimal("50000.00"), "VND");
        broken.setPublicId(UUID.randomUUID());
        broken.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        Order healthy = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_008L,
                new BigDecimal("50000.00"), "VND");
        healthy.setPublicId(UUID.randomUUID());
        healthy.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        when(orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                any(OrderStatus.class), any(Instant.class))).thenReturn(List.of(broken, healthy));
        when(orderPersistenceService.expireIfPending(broken.getPublicId()))
                .thenThrow(new org.springframework.dao.CannotAcquireLockException("lock timeout"));
        when(orderPersistenceService.expireIfPending(healthy.getPublicId())).thenReturn(true);

        assertThat(service.expireDueOrders()).isEqualTo(1);

        verify(orderPersistenceService).expireIfPending(healthy.getPublicId());
    }
}
