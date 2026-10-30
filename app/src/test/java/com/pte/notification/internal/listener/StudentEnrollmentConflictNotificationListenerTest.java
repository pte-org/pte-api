package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.StudentEnrollmentConflictEvent;
import com.pte.session.dto.response.SlotConflict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentEnrollmentConflictNotificationListenerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private NotificationDispatchService dispatchService;

    private StudentEnrollmentConflictNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new StudentEnrollmentConflictNotificationListener(identityService, dispatchService);
    }

    @Test
    void notifiesEveryHostAdminWithConflictDetails() {
        UUID tenantId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID conflictingSessionId = UUID.randomUUID();
        User hostAdmin = new User();
        hostAdmin.setPublicId(UUID.randomUUID());
        when(identityService.findActiveByTenantIdAndRole(tenantId, Role.HOST_ADMIN)).thenReturn(List.of(hostAdmin));

        listener.onStudentEnrollmentConflict(new StudentEnrollmentConflictEvent(sessionId, tenantId,
                "October exam", List.of(new SlotConflict(studentId, conflictingSessionId, "Earlier exam"))));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> dedupeKey = ArgumentCaptor.forClass(String.class);
        verify(dispatchService).dispatchTo(eq(NotificationType.STUDENT_ENROLLMENT_CONFLICT), eq(hostAdmin),
                eq(tenantId), eq("Students skipped because of exam time conflicts"), body.capture(),
                dedupeKey.capture());
        assertThat(body.getValue()).contains("October exam", studentId.toString(), "Earlier exam");
        assertThat(dedupeKey.getValue()).isEqualTo("student-enrollment-conflict:%s:%s:%s".formatted(
                sessionId, hostAdmin.getPublicId(), (studentId + " -> Earlier exam").hashCode()));
    }
}
