package com.pte.notification.internal.listener;

import com.pte.identity.IdentityService;
import com.pte.identity.domain.User;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.session.dto.event.SessionCancelledEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionCancelledNotificationListenerTest {

    @Mock
    private IdentityService identityService;

    @Mock
    private NotificationDispatchService dispatchService;

    @Test
    void onSessionCancelled_dispatchesOneNotificationPerEnrolledStudent() {
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        User first = new User();
        first.setPublicId(firstId);
        first.setEmail("first@example.com");
        User second = new User();
        second.setPublicId(secondId);
        second.setEmail("second@example.com");
        when(identityService.findById(firstId)).thenReturn(Optional.of(first));
        when(identityService.findById(secondId)).thenReturn(Optional.of(second));

        new SessionCancelledNotificationListener(identityService, dispatchService)
                .onSessionCancelled(new SessionCancelledEvent(UUID.randomUUID(), UUID.randomUUID(),
                        List.of(firstId, secondId)));

        ArgumentCaptor<NotificationType> types = ArgumentCaptor.forClass(NotificationType.class);
        verify(dispatchService, times(2)).dispatch(types.capture(), any(), any(), any(), any());
        assertThat(types.getAllValues()).containsOnly(NotificationType.SESSION_CANCELLED);
    }
}
