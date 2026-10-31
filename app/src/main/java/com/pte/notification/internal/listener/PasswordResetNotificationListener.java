package com.pte.notification.internal.listener;

import com.pte.identity.PasswordResetEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.notification.internal.template.BrandedEmailTemplate;
import com.pte.notification.internal.template.RenderedEmail;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Year;

/** Mails password reset links as a card with a plain-text twin, without keeping them in notification history. */
@Component
public class PasswordResetNotificationListener {

    private final NotificationDispatchService dispatchService;
    private final Clock clock;

    public PasswordResetNotificationListener(NotificationDispatchService dispatchService, Clock clock) {
        this.dispatchService = dispatchService;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onPasswordResetRequested(PasswordResetEmailRequestedEvent event) {
        RenderedEmail email = BrandedEmailTemplate.passwordReset(event.links(), event.expiresInMinutes(),
                Year.now(clock).getValue());
        dispatchService.dispatchExternalSensitive(NotificationType.PASSWORD_RESET_LINK_SENT, event.email(), null,
                "password-reset:" + event.requestId(), email.subject(), email.text(), email.html());
    }
}
