package com.pte.billing.internal.repository;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.OrderPayment;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Payments are a child table of the order: saved, reloaded and mutated only through the order aggregate. */
@DataJpaTest
class OrderPaymentRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void paymentsArePersistedWithTheOrderAndReloadedFromIt() {
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), 4_200_001L, new BigDecimal("50000.00"), "VND");
        OrderPayment first = order.receivePayment("{\"first\":true}", Instant.parse("2026-10-24T01:00:00Z"));
        order.receivePayment("{\"second\":true}", Instant.parse("2026-10-24T01:05:00Z"));
        first.markProcessed();
        orderRepository.saveAndFlush(order);
        entityManager.clear();

        Order reloaded = orderRepository.findByOrderCodeForUpdate(4_200_001L).orElseThrow();

        assertThat(reloaded.getPayments()).hasSize(2);
        assertThat(reloaded.getPayments()).extracting(OrderPayment::isProcessed)
                .containsExactlyInAnyOrder(true, false);
        assertThat(reloaded.getPayments()).extracting(OrderPayment::getRawPayload)
                .containsExactlyInAnyOrder("{\"first\":true}", "{\"second\":true}");
    }
}
