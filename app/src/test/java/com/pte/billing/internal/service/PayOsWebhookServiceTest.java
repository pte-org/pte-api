package com.pte.billing.internal.service;

import com.pte.billing.domain.Order;
import com.pte.billing.domain.PaymentTransaction;
import com.pte.billing.domain.Plan;
import com.pte.billing.domain.enums.ActivationSource;
import com.pte.billing.domain.enums.OrderStatus;
import com.pte.billing.domain.enums.PlanStatus;
import com.pte.billing.domain.enums.PlanType;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.PaymentWebhookException;
import com.pte.billing.internal.repository.OrderRepository;
import com.pte.billing.internal.repository.PlanRepository;
import com.pte.billing.internal.vendor.payos.PayOsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
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
    private PaymentTransactionPersistenceService paymentTransactionPersistenceService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PlanRepository planRepository;

    @Mock
    private SubscriptionActivationService subscriptionActivationService;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private PayOsWebhookService service;

    @BeforeEach
    void setUp() {
        service = new PayOsWebhookService(jsonMapper, payOsClient,
                paymentTransactionPersistenceService, orderRepository, planRepository,
                subscriptionActivationService);
    }

    @Test
    void handle_duplicateSuccessfulWebhookActivatesOnlyOnce() {
        UUID tenantId = UUID.randomUUID();
        UUID planId = UUID.randomUUID();
        Order order = Order.pending(tenantId, planId, ORDER_CODE, new BigDecimal("50000.00"), "VND");
        Plan plan = activePlan(planId);
        String payload = successfulPayload(ORDER_CODE, "signed");

        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("signed")))
                .thenReturn(true);
        when(paymentTransactionPersistenceService.record(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(orderRepository.findByOrderCodeForUpdate(ORDER_CODE)).thenReturn(Optional.of(order));
        when(planRepository.findByPublicId(planId)).thenReturn(Optional.of(plan));

        service.handle(payload);
        service.handle(payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(subscriptionActivationService, times(1))
                .activate(tenantId, plan, ActivationSource.PAYMENT);
        verify(orderRepository, times(2)).findByOrderCodeForUpdate(ORDER_CODE);
    }

    @Test
    void handle_invalidSignatureAuditsAndDoesNotActivate() {
        String payload = successfulPayload(ORDER_CODE, "forged");
        when(payOsClient.verifyWebhookSignature(any(JsonNode.class), org.mockito.ArgumentMatchers.eq("forged")))
                .thenReturn(false);

        assertThatThrownBy(() -> service.handle(payload))
                .isInstanceOfSatisfying(PaymentWebhookException.class, ex ->
                        assertThat(ex.getMessage()).isEqualTo(BillingConstants.PAYOS_SIGNATURE_INVALID));

        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactionPersistenceService).recordRejected(captor.capture());
        assertThat(captor.getValue().getOrderCode()).isEqualTo(ORDER_CODE);
        assertThat(captor.getValue().isSignatureValid()).isFalse();
        verify(paymentTransactionPersistenceService, never()).record(any());
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
        when(paymentTransactionPersistenceService.record(any(PaymentTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
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

    private Plan activePlan(UUID publicId) {
        Plan plan = new Plan();
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
