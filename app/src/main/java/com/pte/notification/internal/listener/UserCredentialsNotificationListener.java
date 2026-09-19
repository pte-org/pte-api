package com.pte.notification.internal.listener;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Queues a fresh temporary credential without retaining it in notification history. */
@Component
public class UserCredentialsNotificationListener {

    private final NotificationDispatchService dispatchService;

    public UserCredentialsNotificationListener(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @TransactionalEventListener
    public void onCredentialsRequested(UserCredentialsEmailRequestedEvent event) {
        String subject = "PTE Prep - Your temporary login credentials";
        String body = """
                Hello %s,

                A temporary password has been generated for your PTE Prep account.

                Username: %s
                Temporary password: %s

                Please change the temporary password after your first sign-in.

                Regards,
                PTE Prep Platform Team
                """.formatted(event.fullName() == null ? "there" : event.fullName(), event.username(),
                event.temporaryPassword());

        dispatchService.dispatchExternalSensitive(NotificationType.USER_CREDENTIALS_SENT, event.email(),
                event.tenantId(), "user-credentials:" + event.requestId(), subject, body);
    }
}
