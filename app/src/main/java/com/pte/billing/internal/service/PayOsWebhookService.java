package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.PaymentTransaction;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.PaymentWebhookException;
import com.pte.billing.internal.exception.PlanNotFoundException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;

/** Verifies, audits, and applies PayOS payment notifications idempotently. */
@Service
public class PayOsWebhookService {

    private final JsonMapper jsonMapper;
    private final PayOsClient payOsClient;
    private final PaymentTransactionPersistenceService paymentTransactionPersistenceService;
    private final OrderRepository orderRepository;
    private final PlanRepository planRepository;
    private final SubscriptionActivationService subscriptionActivationService;

    public PayOsWebhookService(JsonMapper jsonMapper, PayOsClient payOsClient,
            PaymentTransactionPersistenceService paymentTransactionPersistenceService,
            OrderRepository orderRepository, PlanRepository planRepository,
            SubscriptionActivationService subscriptionActivationService) {
        this.jsonMapper = jsonMapper;
        this.payOsClient = payOsClient;
        this.paymentTransactionPersistenceService = paymentTransactionPersistenceService;
        this.orderRepository = orderRepository;
        this.planRepository = planRepository;
        this.subscriptionActivationService = subscriptionActivationService;
    }

    @Transactional
    public void handle(String rawPayload) {
        JsonNode payload = parsePayload(rawPayload);
        Long orderCode = extractOrderCode(payload);
        if (!payOsClient.verifyWebhookSignature(payload, payload.path("signature").asText(null))) {
            paymentTransactionPersistenceService.recordRejected(
                    PaymentTransaction.received(orderCode, rawPayload, false, Instant.now()));
            throw new PaymentWebhookException(BillingConstants.PAYOS_SIGNATURE_INVALID);
        }

        PaymentTransaction transaction = paymentTransactionPersistenceService.record(
                PaymentTransaction.received(orderCode, rawPayload, true, Instant.now()));
        if (!isSuccessfulPayment(payload)) {
            transaction.markProcessed();
            return;
        }
        if (orderCode == null) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_WEBHOOK_INVALID);
        }

        Order order = orderRepository.findByOrderCodeForUpdate(orderCode)
                .orElseThrow(() -> new PaymentWebhookException(BillingConstants.PAYOS_ORDER_NOT_FOUND));
        if (order.getStatus() == OrderStatus.PAID) {
            // The row lock makes this branch safe when PayOS retries arrive
            // concurrently with the first webhook.
            transaction.markProcessed();
            return;
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            // An expired/cancelled order must never be resurrected by a late
            // notification, but the valid notification is still acknowledged.
            transaction.markProcessed();
            return;
        }

        verifyOrderAmounts(order, payload.path("data"));
        Plan plan = planRepository.findByPublicId(order.getPlanId())
                .orElseThrow(PlanNotFoundException::new);
        subscriptionActivationService.activate(order.getTenantId(), plan, ActivationSource.PAYMENT);
        order.markPaid(Instant.now());
        transaction.markProcessed();
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
            throw new PaymentWebhookException(BillingConstants.PAYOS_ORDER_AMOUNT_MISMATCH);
        }

        String currency = data.path("currency").asText(null);
        if (currency == null || !order.getCurrency().equalsIgnoreCase(currency)) {
            throw new PaymentWebhookException(BillingConstants.PAYOS_ORDER_CURRENCY_MISMATCH);
        }
    }
}
