package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptPublishedNotificationListenerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private NotificationDispatchService dispatchService;

    private AttemptPublishedNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new AttemptPublishedNotificationListener(identityService, dispatchService);
    }

    @Test
    void onAttemptPublished_userFound_dispatchesNotification() {
        UUID studentPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User student = new User();
        student.setPublicId(studentPublicId);
        student.setEmail("student@example.com");

        AttemptPublishedEvent event = new AttemptPublishedEvent(attemptPublicId, sessionPublicId, studentPublicId, tenantId);

        when(identityService.findById(studentPublicId)).thenReturn(Optional.of(student));

        listener.onAttemptPublished(event);

        verify(dispatchService).dispatch(NotificationType.ATTEMPT_PUBLISHED, student, tenantId,
                "Your exam report is ready",
                "Your report for attempt " + attemptPublicId + " is now available. Log in to view your score.");
    }

    @Test
    void onAttemptPublished_userNotFound_dispatchesWithNullRecipient() {
        UUID studentPublicId = UUID.randomUUID();
        UUID attemptPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        AttemptPublishedEvent event = new AttemptPublishedEvent(attemptPublicId, sessionPublicId, studentPublicId, tenantId);

        when(identityService.findById(studentPublicId)).thenReturn(Optional.empty());

        listener.onAttemptPublished(event);

        verify(dispatchService).dispatch(NotificationType.ATTEMPT_PUBLISHED, null, tenantId,
                "Your exam report is ready",
                "Your report for attempt " + attemptPublicId + " is now available. Log in to view your score.");
    }
}
