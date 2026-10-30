package com.pte.billing.internal.service.order;

import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.order.OrderException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/** Small transaction boundaries for order reservation and lifecycle updates. */
@Service
public class OrderPersistenceService {

    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ServicePlanRepository planRepository;
    private final SubscriptionActivationService subscriptionActivationService;
    private final Clock clock;

    @Autowired
    public OrderPersistenceService(OrderRepository orderRepository, ApplicationEventPublisher eventPublisher,
            ServicePlanRepository planRepository, SubscriptionActivationService subscriptionActivationService,
            Clock clock) {
        this.clock = clock;
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.planRepository = planRepository;
        this.subscriptionActivationService = subscriptionActivationService;
    }

    /** Commits the pending reservation before the remote PayOS call begins. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order reserve(Order order) throws DataIntegrityViolationException {
        ServicePlan plan = planRepository.findByPublicIdForUpdate(order.getPlanId()).filter(p -> !p.isDeleted())
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

    /** Activates a zero-price checkout without opening an external payment link. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Order completeFreeOrder(UUID publicId) {
        Order order = orderRepository.findByPublicIdForUpdateAndDeletedFalse(publicId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND,
                        BillingConstants.ORDER_NOT_FOUND));
        if (order.getStatus() != OrderStatus.PENDING) {
            return order;
        }

        ServicePlan plan = planRepository.findByPublicIdForUpdate(order.getPlanId()).filter(p -> !p.isDeleted())
                .filter(p -> p.getStatus() == PlanStatus.ACTIVE)
                .orElseThrow(() -> new OrderException(HttpStatus.CONFLICT,
                        BillingConstants.ORDER_PLAN_NOT_ACTIVE));
        SubscriptionActivationResponse activation = subscriptionActivationService.activate(
                order.getTenantId(), plan, ActivationSource.PAYMENT);
        order.markPaid(clock.instant());
        orderRepository.saveAndFlush(order);
        eventPublisher.publishEvent(new CommercialOutcomeConfirmedEvent(
                order.getTenantId(),
                plan.getPublicId(),
                plan.getType() == PlanType.EXAM_PACKAGE
                        ? CommercialOutcomeType.EXAM_PACKAGE : CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.PAYMENT,
                activation.kind() == SubscriptionActivationResponse.ActivationKind.SUBSCRIPTION
                        ? CommercialActivationTarget.SUBSCRIPTION : CommercialActivationTarget.QUOTA,
                activation.targetPublicId(),
                activation.grantedStudentSlots(),
                activation.startsAt()));
        return order;
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
