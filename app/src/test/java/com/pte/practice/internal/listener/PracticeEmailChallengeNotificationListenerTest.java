package com.pte.practice.internal.listener;

import com.pte.notification.NotificationService;
import com.pte.practice.internal.event.PracticeEmailChallengeRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PracticeEmailChallengeNotificationListenerTest {

    @Mock
    private NotificationService notificationService;

    @Test
    void dispatchesChallengeThroughTheNotificationBoundary() {
        PracticeEmailChallengeNotificationListener listener =
                new PracticeEmailChallengeNotificationListener(notificationService);
        UUID challengeId = UUID.randomUUID();
        PracticeEmailChallengeRequestedEvent event = new PracticeEmailChallengeRequestedEvent(
                "student@example.com", challengeId, "subject", "sensitive body");

        listener.onChallengeRequested(event);

        verify(notificationService).dispatchPracticeEmailChallenge(
                "student@example.com", challengeId, "subject", "sensitive body");
    }
}
