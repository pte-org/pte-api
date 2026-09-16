package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
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
        service = new OrderExpirationService(orderRepository, orderPersistenceService, payOsClient, 24);
    }

    @Test
    void expireDueOrders_cancelsPayOsLinkBeforeExpiringPendingOrder() {
        UUID publicId = UUID.randomUUID();
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 1_000_000_002L,
                new BigDecimal("50000.00"), "VND");
        order.setPublicId(publicId);
        order.setCreatedAt(Instant.now().minusSeconds(48 * 3_600L));
        when(orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                any(OrderStatus.class), any(Instant.class))).thenReturn(List.of(order));
        when(orderPersistenceService.expireIfPending(publicId)).thenReturn(true);

        int expired = service.expireDueOrders();

        assertThat(expired).isEqualTo(1);
        verify(payOsClient).cancelPaymentLink(order.getOrderCode());
        verify(orderPersistenceService).expireIfPending(publicId);
    }
}
