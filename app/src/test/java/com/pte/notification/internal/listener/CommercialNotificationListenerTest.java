package com.pte.notification.internal.listener;

import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommercialNotificationListenerTest {

    @Mock
    private IdentityService identity;

    @Mock
    private TenancyService tenancy;

    @Mock
    private InboxAppendService appender;

    private UUID tenantId;
    private UUID hostId;

    @BeforeEach
    void setUp() {
        tenantId = UUID.randomUUID();
        hostId = UUID.randomUUID();
    }

    private void stubActiveAudience() {
        when(tenancy.isActiveTenant(tenantId)).thenReturn(true);
        when(identity.findActiveRoleMembers(eq(tenantId), eq(Role.HOST_ADMIN)))
                .thenReturn(List.of(new IdentityRoleMember(hostId, tenantId)));
    }

    @Test
    void paymentExamPackageMapsToOneOrderTargetForActiveTenantHosts() {
        UUID orderId = UUID.randomUUID();
        stubActiveAudience();
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onCommercialOutcomeConfirmed(new CommercialOutcomeConfirmedEvent(
                tenantId, UUID.randomUUID(), CommercialOutcomeType.EXAM_PACKAGE,
                CommercialActivationSource.PAYMENT, CommercialActivationTarget.ORDER, orderId, null));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.type()).isEqualTo(InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED);
        assertThat(request.category()).isEqualTo(InboxCategory.BILLING);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.ORDER);
        assertThat(request.targetPublicId()).isEqualTo(orderId);
        assertThat(request.recipients()).extracting("userPublicId").containsExactly(hostId);
    }

    @Test
    void licenseCapacityMapsToExactQuotaTargetAndIncludesGrantedSlots() {
        UUID quotaId = UUID.randomUUID();
        stubActiveAudience();
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onCommercialOutcomeConfirmed(new CommercialOutcomeConfirmedEvent(
                tenantId, UUID.randomUUID(), CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.LICENSE_CODE, CommercialActivationTarget.QUOTA, quotaId, 40));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.targetType()).isEqualTo(InboxTargetType.QUOTA);
        assertThat(request.targetPublicId()).isEqualTo(quotaId);
        assertThat(request.body()).contains("40");
    }

    @Test
    void inactiveTenantSuppressesCommercialNotice() {
        when(tenancy.isActiveTenant(tenantId)).thenReturn(false);
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onCommercialOutcomeConfirmed(new CommercialOutcomeConfirmedEvent(
                tenantId, UUID.randomUUID(), CommercialOutcomeType.EXAM_PACKAGE,
                CommercialActivationSource.PAYMENT, CommercialActivationTarget.ORDER, UUID.randomUUID(), null));

        verify(identity, never()).findActiveRoleMembers(any(), any());
        verify(appender, never()).append(any());
    }

    @Test
    void orderExpiryTargetsTheExactOrder() {
        UUID orderId = UUID.randomUUID();
        stubActiveAudience();
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onOrderExpired(new OrderExpiredEvent(tenantId, orderId, UUID.randomUUID(), 123L));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.type()).isEqualTo(InboxNotificationType.ORDER_EXPIRED);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.ORDER);
        assertThat(request.targetPublicId()).isEqualTo(orderId);
    }

    @Test
    void revocationTargetsTheExactSubscriptionAndCarriesReason() {
        UUID subscriptionId = UUID.randomUUID();
        stubActiveAudience();
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onSubscriptionRevoked(new SubscriptionRevokedEvent(
                subscriptionId, tenantId, UUID.randomUUID(), "fraud review"));

        InboxNotificationRequested request = capturedRequest();
        assertThat(request.type()).isEqualTo(InboxNotificationType.SUBSCRIPTION_REVOKED);
        assertThat(request.targetType()).isEqualTo(InboxTargetType.SUBSCRIPTION);
        assertThat(request.targetPublicId()).isEqualTo(subscriptionId);
        assertThat(request.body()).contains("fraud review");
    }

    @Test
    void malformedCapacityOutcomeDoesNotCreateANotice() {
        var listener = new CommercialNotificationListener(identity, tenancy, appender);

        listener.onCommercialOutcomeConfirmed(new CommercialOutcomeConfirmedEvent(
                tenantId, UUID.randomUUID(), CommercialOutcomeType.STUDENT_CAPACITY,
                CommercialActivationSource.PAYMENT, CommercialActivationTarget.ORDER,
                UUID.randomUUID(), 0));

        verify(appender, never()).append(any());
    }

    private InboxNotificationRequested capturedRequest() {
        ArgumentCaptor<InboxNotificationRequested> captor =
                ArgumentCaptor.forClass(InboxNotificationRequested.class);
        verify(appender).append(captor.capture());
        return captor.getValue();
    }
}
