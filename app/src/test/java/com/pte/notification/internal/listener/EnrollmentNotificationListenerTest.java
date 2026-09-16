package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.StudentEnrolledEvent;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentNotificationListenerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private NotificationDispatchService dispatchService;

    private EnrollmentNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new EnrollmentNotificationListener(identityService, dispatchService);
    }

    @Test
    void onStudentEnrolled_userFound_dispatchesNotification() {
        UUID studentPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User student = new User();
        student.setPublicId(studentPublicId);
        student.setEmail("student@example.com");

        StudentEnrolledEvent event = new StudentEnrolledEvent(studentPublicId, sessionPublicId, tenantId);

        when(identityService.findById(any(UUID.class))).thenReturn(Optional.of(student));

        listener.onStudentEnrolled(event);

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(dispatchService).dispatch(typeCaptor.capture(), userCaptor.capture(), any(UUID.class),
                any(String.class), any(String.class));

        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.STUDENT_ENROLLED);
        assertThat(userCaptor.getValue()).isEqualTo(student);
    }

    @Test
    void onStudentEnrolled_userNotFound_dispatchesWithNullRecipient() {
        UUID studentPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        StudentEnrolledEvent event = new StudentEnrolledEvent(studentPublicId, sessionPublicId, tenantId);

        when(identityService.findById(any(UUID.class))).thenReturn(Optional.empty());

        listener.onStudentEnrolled(event);

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(dispatchService).dispatch(typeCaptor.capture(), userCaptor.capture(), any(UUID.class),
                any(String.class), any(String.class));

        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.STUDENT_ENROLLED);
        assertThat(userCaptor.getValue()).isNull();
    }
}
