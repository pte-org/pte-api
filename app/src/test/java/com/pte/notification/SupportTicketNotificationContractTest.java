package com.pte.notification;

import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.exception.InboxNotificationException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThat;

class SupportTicketNotificationContractTest {
    private static final UUID USER = UUID.fromString("10000000-0000-0000-0000-000000000011");
    private static final UUID TENANT = UUID.fromString("20000000-0000-0000-0000-000000000011");
    private static final UUID TICKET = UUID.fromString("30000000-0000-0000-0000-000000000011");

    @Test
    void submissionUsesPlatformScopedSupportTarget() {
        InboxNotificationRequested request = new InboxNotificationRequested(
                1,
                "support-ticket:" + TICKET + ":submitted",
                InboxNotificationType.SUPPORT_TICKET_SUBMITTED,
                InboxCategory.SUPPORT,
                InboxImportance.INFO,
                "New feedback received",
                "New BUG feedback was submitted for admin review.",
                InboxTargetType.SUPPORT_TICKET,
                TICKET,
                List.of(new InboxRecipient(USER, null)));

        assertThat(request.recipients()).containsExactly(new InboxRecipient(USER, null));
        assertThat(request.targetType()).isEqualTo(InboxTargetType.SUPPORT_TICKET);
    }

    @Test
    void noteAndStatusRequireTenantScopedRecipients() {
        assertThatExceptionOfType(InboxNotificationException.class).isThrownBy(() -> new InboxNotificationRequested(
                1,
                "support-ticket:" + TICKET + ":note:40000000-0000-0000-0000-000000000011",
                InboxNotificationType.SUPPORT_TICKET_NOTE_ADDED,
                InboxCategory.SUPPORT,
                InboxImportance.INFO,
                "Admin response",
                "An admin responded to your BUG feedback ticket.",
                InboxTargetType.SUPPORT_TICKET,
                TICKET,
                List.of(new InboxRecipient(USER, null))));

        assertThat(new InboxNotificationRequested(
                1,
                "support-ticket:" + TICKET + ":status:OPEN:IN_PROGRESS",
                InboxNotificationType.SUPPORT_TICKET_STATUS_CHANGED,
                InboxCategory.SUPPORT,
                InboxImportance.INFO,
                "Feedback status updated",
                "Your BUG feedback ticket is now IN_PROGRESS.",
                InboxTargetType.SUPPORT_TICKET,
                TICKET,
                List.of(new InboxRecipient(USER, TENANT))).recipients())
                .containsExactly(new InboxRecipient(USER, TENANT));
    }
}
