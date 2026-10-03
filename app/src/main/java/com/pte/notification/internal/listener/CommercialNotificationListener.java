package com.pte.notification.internal.listener;

import com.pte.billing.CommercialActivationTarget;
import com.pte.billing.CommercialActivationSource;
import com.pte.billing.CommercialOutcomeConfirmedEvent;
import com.pte.billing.CommercialOutcomeType;
import com.pte.billing.OrderExpiredEvent;
import com.pte.billing.SubscriptionRevokedEvent;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.service.InboxAppendService;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Maps committed billing transitions to the tenant host inbox. */
@Component
public class CommercialNotificationListener {

    private final IdentityService identity;
    private final TenancyService tenancy;
    private final InboxAppendService appender;

    public CommercialNotificationListener(IdentityService identity, TenancyService tenancy,
            InboxAppendService appender) {
        this.identity = identity;
        this.tenancy = tenancy;
        this.appender = appender;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onCommercialOutcomeConfirmed(CommercialOutcomeConfirmedEvent event) {
        if (event == null || event.tenantPublicId() == null || event.planPublicId() == null
                || event.targetPublicId() == null || event.outcomeType() == null
                || event.activationSource() == null || event.targetType() == null
                || !validOutcome(event)) {
            return;
        }
        InboxTargetType targetType = mapTarget(event.targetType());
        if (targetType == null) {
            return;
        }
        String body = event.outcomeType() == CommercialOutcomeType.EXAM_PACKAGE
                ? InboxConstants.COMMERCIAL_EXAM_BODY.formatted(event.activationSource().name())
                : InboxConstants.COMMERCIAL_CAPACITY_BODY.formatted(
                        event.grantedStudentSlots() == null ? 0 : event.grantedStudentSlots(),
                        event.activationSource().name());
        append(event.tenantPublicId(), "commercial:%s:confirmed".formatted(event.targetPublicId()),
                InboxNotificationType.COMMERCIAL_OUTCOME_CONFIRMED, InboxConstants.COMMERCIAL_NOTIFICATION_TITLE,
                body, targetType, event.targetPublicId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onOrderExpired(OrderExpiredEvent event) {
        if (event == null || event.tenantPublicId() == null || event.orderPublicId() == null) {
            return;
        }
        append(event.tenantPublicId(), "order:%s:expired".formatted(event.orderPublicId()),
                InboxNotificationType.ORDER_EXPIRED, InboxConstants.ORDER_EXPIRED_NOTIFICATION_TITLE,
                InboxConstants.ORDER_EXPIRED_NOTIFICATION_BODY.formatted(
                        event.orderCode() == null ? event.orderPublicId() : event.orderCode()),
                InboxTargetType.ORDER, event.orderPublicId());
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void onSubscriptionRevoked(SubscriptionRevokedEvent event) {
        if (event == null || event.tenantPublicId() == null || event.subscriptionPublicId() == null) {
            return;
        }
        String reason = safeReason(event.reason());
        append(event.tenantPublicId(), "subscription:%s:revoked".formatted(event.subscriptionPublicId()),
                InboxNotificationType.SUBSCRIPTION_REVOKED,
                InboxConstants.SUBSCRIPTION_REVOKED_NOTIFICATION_TITLE,
                InboxConstants.SUBSCRIPTION_REVOKED_NOTIFICATION_BODY.formatted(
                        event.subscriptionPublicId(), reason.isBlank() ? "" : " (" + reason + ")"),
                InboxTargetType.SUBSCRIPTION, event.subscriptionPublicId());
    }

    private void append(UUID tenantId, String eventKey, InboxNotificationType type, String title, String body,
            InboxTargetType targetType, UUID targetPublicId) {
        if (!tenancy.isActiveTenant(tenantId)) {
            return;
        }
        List<InboxRecipient> recipients = identity.findActiveRoleMembers(tenantId, Role.HOST_ADMIN).stream()
                .filter(member -> member.userPublicId() != null && tenantId.equals(member.tenantId()))
                .map(member -> new InboxRecipient(member.userPublicId(), tenantId))
                .sorted(Comparator.comparing(InboxRecipient::userPublicId))
                .toList();
        appender.append(new InboxNotificationRequested(
                InboxConstants.SCHEMA_VERSION,
                eventKey,
                type,
                InboxCategory.BILLING,
                InboxImportance.INFO,
                title,
                body,
                targetType,
                targetPublicId,
                recipients));
    }

    private InboxTargetType mapTarget(CommercialActivationTarget target) {
        return switch (target) {
            case ORDER -> InboxTargetType.ORDER;
            case SUBSCRIPTION -> InboxTargetType.SUBSCRIPTION;
            case QUOTA -> InboxTargetType.QUOTA;
        };
    }

    private boolean validOutcome(CommercialOutcomeConfirmedEvent event) {
        return switch (event.outcomeType()) {
            case EXAM_PACKAGE -> event.targetType() == CommercialActivationTarget.ORDER
                    || event.targetType() == CommercialActivationTarget.SUBSCRIPTION;
            case STUDENT_CAPACITY -> (event.targetType() == CommercialActivationTarget.ORDER
                    || event.targetType() == CommercialActivationTarget.QUOTA)
                    && event.grantedStudentSlots() != null && event.grantedStudentSlots() > 0;
        };
    }

    private String safeReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return "";
        }
        String sanitized = reason.replaceAll("[\\p{Cntrl}&&[^\\r\\n]]", " ").trim();
        return sanitized.length() <= InboxConstants.BILLING_REASON_LIMIT
                ? sanitized : sanitized.substring(0, InboxConstants.BILLING_REASON_LIMIT);
    }
}
