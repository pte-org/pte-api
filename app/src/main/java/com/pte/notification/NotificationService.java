package com.pte.notification;

import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Public outbound-notification boundary for other modules. */
@Service
public class NotificationService {

    private final NotificationDispatchService dispatchService;

    public NotificationService(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    public void dispatchPracticeEmailChallenge(String recipientEmail, UUID challengeId,
            String subject, String body) {
        dispatchService.dispatchExternalSensitive(NotificationType.PRACTICE_EMAIL_CHALLENGE,
                recipientEmail, null, "practice-email-challenge:" + challengeId, subject, body);
    }
}
