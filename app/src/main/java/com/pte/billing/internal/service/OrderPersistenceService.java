package com.pte.billing.internal.service;

import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.OrderException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.PlanStatus;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher eventPublisher;
    private final PlanRepository planRepository;

    public OrderPersistenceService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher,
            PlanRepository planRepository) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.planRepository = planRepository;
    }

    /** Commits the pending reservation before the remote PayOS call begins. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order reserve(Order order) throws DataIntegrityViolationException {
        Plan plan = planRepository.findByPublicIdForUpdate(order.getPlanId()).filter(p -> !p.isDeleted())
                .orElseThrow(() -> new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PLAN_NOT_ACTIVE));
        if (plan.getStatus() != PlanStatus.ACTIVE) {
            throw new OrderException(HttpStatus.CONFLICT, BillingConstants.ORDER_PLAN_NOT_ACTIVE);
        }
        return orderRepository.saveAndFlush(order);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order attachPaymentLink(UUID publicId, String paymentLinkUrl) {
        Order order = find(publicId);
        order.attachPaymentLink(paymentLinkUrl);
        return orderRepository.saveAndFlush(order);
    }

    @Transactional
    public boolean expireIfPending(UUID publicId) {
        Order order = orderRepository.findByPublicIdForUpdateAndDeletedFalse(publicId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        BillingConstants.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }
        order.expire();
        orderRepository.saveAndFlush(order);
        eventPublisher.publishEvent(new OrderExpiredEvent(
                order.getTenantId(), order.getPublicId(), order.getPlanId(), order.getOrderCode()));
        return true;
    }

    private Order find(UUID publicId) {
        return orderRepository.findByPublicIdAndDeletedFalse(publicId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        BillingConstants.ORDER_NOT_FOUND));
    }
}
