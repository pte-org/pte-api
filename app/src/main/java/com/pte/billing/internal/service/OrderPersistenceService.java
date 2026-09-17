package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.OrderException;
import com.pte.billing.internal.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** Small transaction boundaries for order reservation and lifecycle updates. */
@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;

    public OrderPersistenceService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /** Commits the pending reservation before the remote PayOS call begins. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order reserve(Order order) throws DataIntegrityViolationException {
        return orderRepository.saveAndFlush(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order attachPaymentLink(UUID publicId, String paymentLinkUrl) {
        Order order = find(publicId);
        order.attachPaymentLink(paymentLinkUrl);
        return orderRepository.saveAndFlush(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean expireIfPending(UUID publicId) {
        Order order = find(publicId);
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        order.expire();
        orderRepository.saveAndFlush(order);
        return true;
    }

    private Order find(UUID publicId) {
        return orderRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        BillingConstants.ORDER_NOT_FOUND));
    }
}
