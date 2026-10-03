package com.pte.notification.internal.service;

import com.pte.identity.IdentityRoleMember;
import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.notification.InboxNotificationRequested;
import com.pte.notification.InboxRecipient;
import com.pte.notification.domain.enums.InboxCategory;
import com.pte.notification.domain.enums.InboxImportance;
import com.pte.notification.domain.enums.InboxNotificationType;
import com.pte.notification.domain.enums.InboxTargetType;
import com.pte.notification.internal.constant.InboxConstants;
import com.pte.notification.internal.repository.InboxDeliveryStore;
import com.pte.session.SessionService;
import com.pte.session.dto.response.ClosingSoonSessionView;
import com.pte.tenancy.TenancyService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Revalidates OPEN session windows and appends one durable host reminder per schedule identity. */
@Component
public class SessionClosingSoonReminderWorker {

    private final SessionService sessionService;
    private final IdentityService identityService;
    private final TenancyService tenancyService;
    private final InboxAppendService appender;
    private final InboxDeliveryStore deliveryStore;
    private final Clock clock;
    private final long thresholdSeconds;

    public SessionClosingSoonReminderWorker(SessionService sessionService, IdentityService identityService,
            TenancyService tenancyService, InboxAppendService appender, InboxDeliveryStore deliveryStore,
            Clock clock, @Value("${notification.session-reminder.threshold-seconds:900}") long thresholdSeconds) {
        this.sessionService = sessionService;
        this.identityService = identityService;
        this.tenancyService = tenancyService;
        this.appender = appender;
        this.deliveryStore = deliveryStore;
        this.clock = clock;
        this.thresholdSeconds = thresholdSeconds;
    }

    @Scheduled(fixedDelayString = "${notification.session-reminder.poll-delay-ms:60000}")
    @Transactional
    public void deliverScheduledReminders() {
        deliverDueReminders();
    }

    @Transactional
    public int deliverDueReminders() {
        if (thresholdSeconds <= 0) {
            return 0;
        }
        Instant now = clock.instant();
        Instant cutoff = now.plusSeconds(thresholdSeconds);
        int appended = 0;
        for (var sessionPublicId : deliveryStore.findPendingSessionReminderTargets()) {
            if (sessionService.lockDueClosingSoonSession(sessionPublicId, now, cutoff).isEmpty()) {
                deliveryStore.suppressPendingSessionReminders(sessionPublicId, null, now);
            }
        }
        for (ClosingSoonSessionView candidate : sessionService.findDueClosingSoonSessions(now, cutoff)) {
            var locked = sessionService.lockDueClosingSoonSession(candidate.sessionPublicId(), now, cutoff);
            if (locked.isEmpty()) {
                continue;
            }
            ClosingSoonSessionView session = locked.get();
            String eventKey = "session:%s:closing-soon:%d:%d".formatted(
                    session.sessionPublicId(), session.closesAt().toEpochMilli(), thresholdSeconds);
            deliveryStore.suppressPendingSessionReminders(session.sessionPublicId(), eventKey, now);
            if (!tenancyService.isActiveTenant(session.tenantId())) {
                continue;
            }
            List<InboxRecipient> recipients = identityService
                    .findActiveRoleMembers(session.tenantId(), Role.HOST_ADMIN).stream()
                    .filter(member -> member != null && member.userPublicId() != null
                            && session.tenantId().equals(member.tenantId()))
                    .sorted(Comparator.comparing(IdentityRoleMember::userPublicId))
                    .map(member -> new InboxRecipient(member.userPublicId(), session.tenantId()))
                    .toList();
            appender.append(new InboxNotificationRequested(
                    InboxConstants.SCHEMA_VERSION, eventKey,
                    InboxNotificationType.SESSION_CLOSING_SOON, InboxCategory.SESSION,
                    InboxImportance.IMPORTANT, InboxConstants.SESSION_CLOSING_SOON_NOTIFICATION_TITLE,
                    InboxConstants.SESSION_CLOSING_SOON_NOTIFICATION_BODY.formatted(
                            session.name(), session.closesAt()), InboxTargetType.SESSION,
                    session.sessionPublicId(), recipients));
            appended++;
        }
        return appended;
    }
}
