package com.pte.billing.internal.service.order;

import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.OrderPayment;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.internal.exception.order.PaymentWebhookException;
import com.pte.billing.internal.exception.plan.PlanNotFoundException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;

/** Verifies, audits, and applies PayOS payment notifications idempotently. */
@Service
public class PayOsWebhookService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayOsWebhookService.class);

    private final JsonMapper jsonMapper;
    private final PayOsClient payOsClient;
    private final OrderRepository orderRepository;
    private final ServicePlanRepository planRepository;
    private final SubscriptionActivationService subscriptionActivationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PayOsWebhookService(JsonMapper jsonMapper, PayOsClient payOsClient,
            OrderRepository orderRepository, ServicePlanRepository planRepository,
            SubscriptionActivationService subscriptionActivationService,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.jsonMapper = jsonMapper;
        this.payOsClient = payOsClient;
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.subscriptionActivationService = subscriptionActivationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public void handle(String rawPayload) {
        JsonNode payload = parsePayload(rawPayload);
        Long orderCode = extractOrderCode(payload);
        if (!payOsClient.verifyWebhookSignature(payload, payload.path("signature").asText(null))) {
            LOGGER.warn("Rejected PayOS webhook with an invalid signature, orderCode={}", orderCode);
            throw new PaymentWebhookException(BillingConstants.PAYOS_SIGNATURE_INVALID);
        }
        if (!isSuccessfulPayment(payload)) {
            acknowledgeNonSuccessful(orderCode, rawPayload);
            return;
        }
        if (orderCode == null) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode).orElseThrow(() -> {
            // A signed notification for an order we do not know is the case ops must reconcile by hand.
            LOGGER.warn("Signed PayOS payment for an unknown order, orderCode={} amount={} currency={}",
                    orderCode, payload.path("data").path("amount"), payload.path("data").path("currency"));
            return new PaymentWebhookException(BillingConstants.PAYOS_ORDER_NOT_FOUND);
        });
        OrderPayment receipt = order.receivePayment(rawPayload, clock.instant());
        if (order.getStatus() == OrderStatus.PAID) {
            // The row lock makes this branch safe when PayOS retries arrive
            // concurrently with the first webhook.
            receipt.markProcessed();
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            // An expired/cancelled order must never be resurrected by a late
            // notification, but the valid notification is still acknowledged.
            receipt.markProcessed();
            return;
        }

        verifyOrderAmounts(order, payload.path("data"));
        ServicePlan plan = planRepository.findByPublicId(order.getPlanId())
                .orElseThrow(PlanNotFoundException::new);
        SubscriptionActivationResponse activation = subscriptionActivationService.activate(
                order.getTenantId(), plan, ActivationSource.PAYMENT);
        order.markPaid(clock.instant());
        eventPublisher.publishEvent(new CommercialOutcomeConfirmedEvent(
                order.getTenantId(),
                plan.getPublicId(),
                plan.getType() == PlanType.EXAM_PACKAGE
                        ? CommercialOutcomeType.EXAM_PACKAGE : CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.PAYMENT,
                CommercialActivationTarget.ORDER,
                order.getPublicId(),
                activation.grantedStudentSlots()));
        receipt.markProcessed();
    }

    /** Failed or cancelled payments change nothing; they are kept as a receipt when the order is known. */
    private void acknowledgeNonSuccessful(Long orderCode, String rawPayload) {
        if (orderCode == null) {
            return;
        }
        orderRepository.findByOrderCodeForUpdate(orderCode)
                .ifPresent(order -> order.receivePayment(rawPayload, clock.instant()).markProcessed());
    }

    private JsonNode parsePayload(String rawPayload) {
        if (rawPayload == null || rawPayload.isBlank()) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }
        try {
            JsonNode payload = jsonMapper.readTree(rawPayload);
            if (payload == null || !payload.isObject()) {
                throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
            }
            return payload;
        } catch (PaymentWebhookException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }
    }

    private Long extractOrderCode(JsonNode payload) {
        JsonNode value = payload.path("data").path("orderCode");
        if (!value.isIntegralNumber()) {
            return null;
        }
        return value.longValue();
    }

    private boolean isSuccessfulPayment(JsonNode payload) {
        return payload.path("success").asBoolean(false)
                && BillingConstants.PAYOS_PAYMENT_SUCCESS_CODE.equals(payload.path("code").asText())
                && BillingConstants.PAYOS_PAYMENT_SUCCESS_CODE.equals(
                        payload.path("data").path("code").asText());
    }

    private void verifyOrderAmounts(Order order, JsonNode data) {
        JsonNode amountNode = data.path("amount");
        if (!amountNode.isNumber()) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }
        BigDecimal webhookAmount;
        try {
            webhookAmount = amountNode.decimalValue();
        } catch (RuntimeException ex) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }
        if (order.getAmount().compareTo(webhookAmount) != 0) {
            LOGGER.warn("Signed PayOS payment amount mismatch, orderCode={} expected={} received={}",
                    order.getOrderCode(), order.getAmount(), webhookAmount);
            throw new PaymentWebhookException(BillingConstants.PAYOS_ORDER_AMOUNT_MISMATCH);
        }

        String currency = data.path("currency").asText(null);
        if (currency == null || !order.getCurrency().equalsIgnoreCase(currency)) {
            LOGGER.warn("Signed PayOS payment currency mismatch, orderCode={} expected={} received={}",
                    order.getOrderCode(), order.getCurrency(), currency);
            throw new PaymentWebhookException(BillingConstants.PAYOS_ORDER_CURRENCY_MISMATCH);
        }
    }
}
