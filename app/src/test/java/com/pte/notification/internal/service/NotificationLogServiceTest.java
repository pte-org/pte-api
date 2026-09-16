package com.pte.notification.internal.service;

import com.pte.notification.domain.NotificationLog;
import com.pte.notification.domain.enums.NotificationStatus;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.dto.response.NotificationLogResponse;
import com.pte.notification.internal.mapper.NotificationLogMapper;
import com.pte.notification.internal.repository.NotificationLogRepository;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationLogServiceTest {

    @Mock
    private NotificationLogRepository notificationLogRepository;

    @Mock
    private NotificationLogMapper notificationLogMapper;

    private NotificationLogService service;

    @BeforeEach
    void setUp() {
        service = new NotificationLogService(notificationLogRepository, notificationLogMapper);
    }

    @Test
    void list_delegatesToRepositoryAndMaps() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(userId, tenantId, List.of("HOST_ADMIN"));

        NotificationLog log1 = new NotificationLog();
        log1.setPublicId(UUID.randomUUID());
        log1.setRecipientUserPublicId(UUID.randomUUID());
        log1.setRecipientEmail("student1@example.com");
        log1.setTenantId(tenantId);
        log1.setNotificationType(NotificationType.STUDENT_ENROLLED);
        log1.setSubject("Enrolled");
        log1.setBody("You have been enrolled");
        log1.setStatus(NotificationStatus.SENT);

        NotificationLog log2 = new NotificationLog();
        log2.setPublicId(UUID.randomUUID());
        log2.setRecipientUserPublicId(UUID.randomUUID());
        log2.setRecipientEmail("host@example.com");
        log2.setTenantId(tenantId);
        log2.setNotificationType(NotificationType.VIOLATION_DETECTED);
        log2.setSubject("Violation");
        log2.setBody("A violation was flagged");
        log2.setStatus(NotificationStatus.PENDING);

        when(notificationLogRepository.findByTenantIdOrderByCreatedAtDesc(tenantId))
                .thenReturn(List.of(log1, log2));

        NotificationLogResponse response1 = new NotificationLogResponse(
                log1.getPublicId(), log1.getRecipientEmail(), NotificationType.STUDENT_ENROLLED,
                "Enrolled", NotificationStatus.SENT, log1.getSentAt());
        NotificationLogResponse response2 = new NotificationLogResponse(
                log2.getPublicId(), log2.getRecipientEmail(), NotificationType.VIOLATION_DETECTED,
                "Violation", NotificationStatus.PENDING, log2.getSentAt());

        when(notificationLogMapper.toResponse(log1)).thenReturn(response1);
        when(notificationLogMapper.toResponse(log2)).thenReturn(response2);

        List<NotificationLogResponse> result = service.list(caller);

        assertThat(result).hasSize(2);
        assertThat(result).containsExactly(response1, response2);
        verify(notificationLogRepository).findByTenantIdOrderByCreatedAtDesc(tenantId);
        verify(notificationLogMapper).toResponse(log1);
        verify(notificationLogMapper).toResponse(log2);
    }
}
