package com.pte.billing.internal.service.order;

import com.pte.billing.internal.service.order.PayOsWebhookService;
import com.pte.billing.internal.service.subscription.SubscriptionActivationService;
import com.pte.billing.domain.Order;
import com.pte.billing.domain.OrderPayment;
import com.pte.billing.domain.ServicePlan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.dto.response.SubscriptionActivationResponse;
import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.order.PaymentWebhookException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.ServicePlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PayOsWebhookServiceTest {

    private static final long ORDER_CODE = 1_000_000_001L;

    @Mock
    private PayOsClient payOsClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ServicePlanRepository planRepository;

    @Mock
    private SubscriptionActivationService subscriptionActivationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PayOsWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PayOsWebhookService(jsonMapper, payOsClient,
                orderRepository, planRepository,
                subscriptionActivationService, eventPublisher, com.pte.TestClocks.fixed());
    }

    @Test
    void handle_duplicateSuccessfulWebhookActivatesOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, ORDER_CODE, new BigDecimal("50000.00"), "VND");
        ServicePlan plan = activePlan(planId);
        String payload = successfulPayload(ORDER_CODE, "signed");

        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));
        when(subscriptionActivationService.activate(tenantId, plan, ActivationSource.PAYMENT))
                .thenReturn(SubscriptionActivationResponse.fromSubscription(tenantId, planId,
                        "PTE-EXAM-2026-ABC123", Instant.now(), Instant.now().plusSeconds(3600),
                        25, "ACTIVE", ActivationSource.PAYMENT.name()));

        service.handle(payload);
        service.handle(payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getPayments()).hasSize(2).allMatch(OrderPayment::isProcessed);
        verify(subscriptionActivationService, times(1))
                .activate(tenantId, plan, ActivationSource.PAYMENT);
        verify(eventPublisher, times(1)).publishEvent(any(CommercialOutcomeConfirmedEvent.class));
        verify(orderRepository, times(2)).findByOrderCodeForUpdate(ORDER_CODE);
    }

    @Test
    void handle_successfulPaymentPublishesOneCombinedOutcomeWithExactOrderTarget() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, ORDER_CODE, new BigDecimal("50000.00"), "VND");
        order.setPublicId(orderId);
        ServicePlan plan = activePlan(planId);
        String payload = successfulPayload(ORDER_CODE, "signed");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));
        when(subscriptionActivationService.activate(tenantId, plan, ActivationSource.PAYMENT))
                .thenReturn(SubscriptionActivationResponse.fromSubscription(tenantId, planId,
                        "PTE-EXAM-2026-ABC123", Instant.now(), Instant.now().plusSeconds(3600),
                        25, "ACTIVE", ActivationSource.PAYMENT.name()));

        service.handle(payload);

        ArgumentCaptor<CommercialOutcomeConfirmedEvent> captor =
                ArgumentCaptor.forClass(CommercialOutcomeConfirmedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantPublicId()).isEqualTo(tenantId);
        assertThat(captor.getValue().outcomeType()).isEqualTo(CommercialOutcomeType.EXAM_PACKAGE);
        assertThat(captor.getValue().targetType()).isEqualTo(CommercialActivationTarget.ORDER);
        assertThat(captor.getValue().targetPublicId()).isEqualTo(orderId);
    }

    @Test
    void handle_invalidSignatureIsRejectedWithoutTouchingAnyOrder() {
        String payload = successfulPayload(ORDER_CODE, "forged");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("forged")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.handle(payload))
                .isInstanceOfSatisfying(PaymentWebhookException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.PAYOS_SIGNATURE_INVALID));

        verify(orderRepository, never()).findByOrderCodeForUpdate(any());
        verify(subscriptionActivationService, never()).activate(any(), any(), any());
    }

    @Test
    void handle_successfulPaymentForUnknownOrderIsRejected() {
        String payload = successfulPayload(ORDER_CODE, "signed");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.handle(payload))
                .isInstanceOfSatisfying(PaymentWebhookException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.PAYOS_ORDER_NOT_FOUND));

        verify(subscriptionActivationService, never()).activate(any(), any(), any());
    }

    @Test
    void handle_failedPaymentIsKeptAsAProcessedReceiptAndChangesNothing() {
        Order order = Order.pending(UUID.randomUUID(), UUID.randomUUID(), ORDER_CODE, new BigDecimal("50000.00"), "VND");
        String payload = successfulPayload(ORDER_CODE, "signed").replace("\"code\": \"00\"", "\"code\": \"01\"");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));

        service.handle(payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getPayments()).hasSize(1).allMatch(OrderPayment::isProcessed);
        verify(subscriptionActivationService, never()).activate(any(), any(), any());
    }

    @Test
    void handleAmountMismatchDoesNotMarkOrderPaidOrActivate() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, ORDER_CODE, new BigDecimal("50000.00"), "VND");
        String payload = successfulPayloadWithAmount(ORDER_CODE, "signed", "49000");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.handle(payload))
                .isInstanceOfSatisfying(PaymentWebhookException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.PAYOS_ORDER_AMOUNT_MISMATCH));

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(subscriptionActivationService, never()).activate(any(), any(), any());
    }

    private String successfulPayload(long orderCode, String signature) {
        return successfulPayloadWithAmount(orderCode, signature, "50000");
    }

    private String successfulPayloadWithAmount(long orderCode, String signature, String amount) {
        return """
                {
                  "code": "00",
                  "desc": "success",
                  "success": true,
                  "data": {
                    "orderCode": %d,
                    "amount": %s,
                    "currency": "VND",
                    "code": "00",
                    "desc": "Thành công"
                  },
                  "signature": "%s"
                }
                """.formatted(orderCode, amount, signature);
    }

    private ServicePlan activePlan(UUID publicId) {
        ServicePlan plan = new ServicePlan();
        plan.setPublicId(publicId);
        plan.setName("Exam");
        plan.setType(PlanType.EXAM_PACKAGE);
        plan.setPrice(new BigDecimal("50000.00"));
        plan.setCurrency("VND");
        plan.setDurationDays(30);
        plan.setMaxStudentsPerSession(25);
        plan.setStatus(PlanStatus.ACTIVE);
        return plan;
    }
}
