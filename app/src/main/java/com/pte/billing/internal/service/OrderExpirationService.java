package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.PayOsException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/** Cancels stale PayOS links before expiring their local pending orders. */
@Service
public class OrderExpirationService {

    private static final Logger log = LoggerFactory.getLogger(OrderExpirationService.class);

    private final OrderRepository orderRepository;
    private final OrderPersistenceService orderPersistenceService;
    private final PayOsClient payOsClient;
    private final int pendingOrderTtlHours;

    public OrderExpirationService(OrderRepository orderRepository,
            OrderPersistenceService orderPersistenceService, PayOsClient payOsClient,
            @Value("${billing.order.pending-ttl-hours:" + BillingConstants.DEFAULT_PENDING_ORDER_TTL_HOURS + "}")
            int pendingOrderTtlHours) {
        this.orderRepository = orderRepository;
        this.orderPersistenceService = orderPersistenceService;
        this.payOsClient = payOsClient;
        this.pendingOrderTtlHours = pendingOrderTtlHours;
    }

    @Scheduled(cron = "${billing.order-expiration.cron:0 0 * * * *}")
    public int expireDueOrders() {
        Instant cutoff = Instant.now().minus(Duration.ofHours(pendingOrderTtlHours));
        int expired = 0;
        for (Order order : orderRepository.findByStatusAndDeletedFalseAndCreatedAtLessThanEqual(
                OrderStatus.PENDING, cutoff)) {
            try {
                payOsClient.cancelPaymentLink(order.getOrderCode());
                if (orderPersistenceService.expireIfPending(order.getPublicId())) {
                    expired++;
                }
            } catch (PayOsException ex) {
                // Keep it PENDING so a transient PayOS outage is retried and
                // the local state never claims a link was cancelled when it was not.
                log.warn("Could not expire pending order {}: {}", order.getOrderCode(), ex.getMessage());
            }
        }
        if (expired > 0) {
            log.info("Expired {} pending billing orders", expired);
        }
        return expired;
    }
}
