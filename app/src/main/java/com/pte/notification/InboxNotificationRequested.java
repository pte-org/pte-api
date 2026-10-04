package com.pte.notification;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.exception.InboxNotificationException;
import org.springframework.http.HttpStatus;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Notification-owned event. Business modules publish their OWN events; their listeners map here. */
public record InboxNotificationRequested(int schemaVersion, String eventKey, InboxNotificationType type,
        InboxCategory category, InboxImportance importance, String title, String body,
        InboxTargetType targetType, UUID targetPublicId, List<InboxRecipient> recipients) {
    public InboxNotificationRequested {
        if (schemaVersion != InboxConstants.SCHEMA_VERSION || !validText(eventKey, InboxConstants.EVENT_KEY_LIMIT)
                || !validText(title, InboxConstants.TITLE_LIMIT) || !validText(body, InboxConstants.BODY_LIMIT)
                || type == null || category == null || importance == null || targetType == null
                || targetPublicId == null || recipients == null || !validMapping(type, category, targetType)) {
            throw invalid();
        }
        Map<UUID, InboxRecipient> audience = new HashMap<>();
        for (InboxRecipient recipient : recipients) {
            if (recipient == null || (recipient.tenantId() == null) != isPlatformScoped(type)) {
                throw invalid();
            }
            InboxRecipient previous = audience.putIfAbsent(recipient.userPublicId(), recipient);
            if (previous != null && !previous.equals(recipient)) { throw invalid(); }
        }
        recipients = audience.values().stream().sorted(Comparator.comparing(InboxRecipient::userPublicId)).toList();
    }

    private static boolean validText(String value, int limit) {
        return value != null && !value.isBlank() && value.length() <= limit && value.indexOf('\0') < 0;
    }

    private static boolean validMapping(InboxNotificationType type, InboxCategory category, InboxTargetType target) {
        return switch (type) {
            case APPLICATION_SUBMITTED -> category == InboxCategory.APPLICATION && target == InboxTargetType.APPLICATION;
            case PLATFORM_ANNOUNCEMENT -> (category == InboxCategory.SYSTEM_NOTICE || category == InboxCategory.MAINTENANCE)
                    && target == InboxTargetType.ANNOUNCEMENT;
            case SESSION_CLOSING_SOON, SESSION_GRADING_COMPLETED -> category == InboxCategory.SESSION && target == InboxTargetType.SESSION;
            case COMMERCIAL_OUTCOME_CONFIRMED -> category == InboxCategory.BILLING &&
                    (target == InboxTargetType.ORDER || target == InboxTargetType.SUBSCRIPTION || target == InboxTargetType.QUOTA);
            case ORDER_EXPIRED -> category == InboxCategory.BILLING && target == InboxTargetType.ORDER;
            case SUBSCRIPTION_REVOKED -> category == InboxCategory.BILLING && target == InboxTargetType.SUBSCRIPTION;
            case SUPPORT_TICKET_SUBMITTED, SUPPORT_TICKET_NOTE_ADDED, SUPPORT_TICKET_STATUS_CHANGED ->
                    category == InboxCategory.SUPPORT && target == InboxTargetType.SUPPORT_TICKET;
        };
    }

    private static boolean isPlatformScoped(InboxNotificationType type) {
        return type == InboxNotificationType.APPLICATION_SUBMITTED
                || type == InboxNotificationType.SUPPORT_TICKET_SUBMITTED;
    }

    private static InboxNotificationException invalid() {
        return new InboxNotificationException(HttpStatus.BAD_REQUEST, InboxConstants.INVALID_REQUEST, InboxConstants.INVALID_REQUEST_MESSAGE);
    }

    @Override public String toString() {
        return "InboxNotificationRequested[type=" + type + ", recipients=" + recipients.size() + "]";
    }
}
