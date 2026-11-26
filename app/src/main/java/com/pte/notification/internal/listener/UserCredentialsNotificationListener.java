package com.pte.notification.internal.listener;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.notification.internal.template.BrandedEmailTemplate;
import com.pte.notification.internal.template.RenderedEmail;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Clock;
import java.time.Year;

/** Queues a fresh temporary credential as a card with a plain-text twin, without retaining it in history. */
@Component
public class UserCredentialsNotificationListener {

    private final NotificationDispatchService dispatchService;
    private final NotificationProperties properties;
    private final TenancyService tenancyService;
    private final Clock clock;

    public UserCredentialsNotificationListener(NotificationDispatchService dispatchService,
            NotificationProperties properties, TenancyService tenancyService, Clock clock) {
        this.dispatchService = dispatchService;
        this.properties = properties;
        this.tenancyService = tenancyService;
        this.clock = clock;
    }

    @TransactionalEventListener
    public void onCredentialsRequested(UserCredentialsEmailRequestedEvent event) {
        String organizationName = event.tenantId() != null
                ? tenancyService.findOrganizationName(event.tenantId()).orElse(null)
                : null;
        String loginUrl = "Student".equalsIgnoreCase(event.roleLabel())
                ? properties.getPracticeLoginUrl()
                : properties.getLoginUrl();
        RenderedEmail email = BrandedEmailTemplate.temporaryCredentials(event.fullName(), organizationName,
                event.roleLabel(), event.username(), event.temporaryPassword(), loginUrl,
                event.newAccount(), Year.now(clock).getValue());
        dispatchService.dispatchExternalSensitive(NotificationType.USER_CREDENTIALS_SENT, event.email(),
                event.tenantId(), "user-credentials:" + event.requestId(), email.subject(), email.text(),
                email.html());
    }
}
