package com.pte.practice.internal.listener;

import com.pte.notification.NotificationService;
import com.pte.practice.internal.event.PracticeEmailChallengeRequestedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/** Enqueues the sensitive verification email only after challenge persistence commits. */
@Component
public class PracticeEmailChallengeNotificationListener {

    private final NotificationService notificationService;

    public PracticeEmailChallengeNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener
    public void onChallengeRequested(PracticeEmailChallengeRequestedEvent event) {
        notificationService.dispatchPracticeEmailChallenge(
                event.recipientEmail(), event.challengeId(), event.subject(), event.body());
    }
}
