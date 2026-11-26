package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.config.NotificationProperties;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.StudentEnrolledEvent;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentNotificationListenerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private NotificationDispatchService dispatchService;

    private EnrollmentNotificationListener listener;

    @BeforeEach
    void setUp() {
        listener = new EnrollmentNotificationListener(identityService, tenancyService, dispatchService,
                new NotificationProperties(), com.pte.TestClocks.fixed());
    }

    @Test
    void onStudentEnrolled_sendsTheSchedule() {
        UUID studentPublicId = UUID.randomUUID();
        UUID sessionPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        User student = new User();
        student.setPublicId(studentPublicId);
        student.setEmail("student@example.com");
        student.setFullName("Hoàng Đức");

        StudentEnrolledEvent event = new StudentEnrolledEvent(sessionPublicId, studentPublicId, tenantId,
                "Mock Test 1", Instant.parse("2026-11-26T06:05:00Z"), Instant.parse("2026-11-26T09:00:00Z"));

        when(identityService.findById(any(UUID.class))).thenReturn(Optional.of(student));
        when(tenancyService.findOrganizationName(tenantId)).thenReturn(Optional.of("Ninh Lê Organization"));

        listener.onStudentEnrolled(event);

        ArgumentCaptor<NotificationType> typeCaptor = ArgumentCaptor.forClass(NotificationType.class);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> textCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);

        verify(dispatchService).dispatch(typeCaptor.capture(), userCaptor.capture(), any(UUID.class),
                subjectCaptor.capture(), textCaptor.capture(), isNull(), htmlCaptor.capture());

        assertThat(typeCaptor.getValue()).isEqualTo(NotificationType.STUDENT_ENROLLED);
        assertThat(userCaptor.getValue()).isEqualTo(student);
        assertThat(subjectCaptor.getValue()).isEqualTo("[PTE Prep] Enrolled: Mock Test 1");
        assertThat(textCaptor.getValue()).contains("Opens: 26 Nov 2026, 13:05 (GMT+7)");
        assertThat(htmlCaptor.getValue()).contains("Hi Hoàng Đức,", "Ninh Lê Organization", "16:00 (GMT+7)");
    }

    @Test
    void onStudentEnrolled_userNotFound_dispatchesWithNullRecipient() {
        UUID tenantId = UUID.randomUUID();
        StudentEnrolledEvent event = new StudentEnrolledEvent(UUID.randomUUID(), UUID.randomUUID(), tenantId);

        when(identityService.findById(any(UUID.class))).thenReturn(Optional.empty());
        when(tenancyService.findOrganizationName(tenantId)).thenReturn(Optional.empty());

        listener.onStudentEnrolled(event);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(dispatchService).dispatch(any(NotificationType.class), userCaptor.capture(), any(UUID.class),
                any(String.class), any(String.class), isNull(), any(String.class));
        assertThat(userCaptor.getValue()).isNull();
    }
}
