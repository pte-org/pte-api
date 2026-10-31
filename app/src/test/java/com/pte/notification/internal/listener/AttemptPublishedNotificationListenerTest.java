package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.reporting.dto.event.AttemptPublishedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptPublishedNotificationListenerTest {

    private final UUID studentPublicId = UUID.randomUUID();
    private final UUID attemptPublicId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();

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
    void emailsTheOverallAndSkillScoresWithoutAskingTheStudentToLogIn() {
        when(identityService.findById(studentPublicId)).thenReturn(Optional.of(student("Nguyen Van A", "a@example.com")));

        listener.onAttemptPublished(event(72, 70, 75, 68, 74));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(eq(NotificationType.ATTEMPT_PUBLISHED),
                eq("a@example.com"), eq(tenantId), eq("attempt-published:" + attemptPublicId),
                eq("Your exam results"), body.capture());
        assertThat(body.getValue()).startsWith("Hello Nguyen Van A,").contains(
                "Overall score: 72", "Listening: 70", "Reading: 75", "Speaking: 68", "Writing: 74");
        assertThat(body.getValue().toLowerCase()).doesNotContain("log in").doesNotContain("login");
    }

    @Test
    void aSkillWithInsufficientDataIsNeverShownAsZero() {
        when(identityService.findById(studentPublicId)).thenReturn(Optional.of(student(null, "a@example.com")));

        listener.onAttemptPublished(event(74, null, null, 74, null));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchExternalSensitive(any(), anyString(), any(), anyString(), anyString(),
                body.capture());
        assertThat(body.getValue()).startsWith("Hello,").contains("Overall score: 74", "Speaking: 74",
                "Listening: Not enough data", "Reading: Not enough data", "Writing: Not enough data");
        assertThat(body.getValue()).doesNotContain(": 0");
    }

    @Test
    void aStudentWhoCannotBeFoundOrHasNoEmailGetsNoMail() {
        when(identityService.findById(studentPublicId)).thenReturn(Optional.empty());
        listener.onAttemptPublished(event(72, 70, 75, 68, 74));

        when(identityService.findById(studentPublicId)).thenReturn(Optional.of(student("No Mail", " ")));
        listener.onAttemptPublished(event(72, 70, 75, 68, 74));

        verify(dispatchService, never()).dispatchExternalSensitive(any(), any(), any(), any(), any(), any());
        verify(dispatchService, never()).dispatch(any(), any(), any(), any(), any());
    }

    private AttemptPublishedEvent event(Integer overall, Integer listening, Integer reading, Integer speaking,
            Integer writing) {
        return new AttemptPublishedEvent(attemptPublicId, UUID.randomUUID(), studentPublicId, tenantId, overall,
                listening, reading, speaking, writing);
    }

    private User student(String fullName, String email) {
        User student = new User();
        student.setPublicId(studentPublicId);
        student.setFullName(fullName);
        student.setEmail(email);
        return student;
    }
}
