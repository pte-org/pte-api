package com.pte.notification.internal.listener;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.service.IdentityService;
import com.pte.notification.domain.enums.NotificationType;
import com.pte.notification.internal.constant.NotificationConstants;
import com.pte.notification.internal.service.NotificationDispatchService;
import com.pte.proctoring.domain.enums.ViolationType;
import com.pte.proctoring.dto.event.ViolationDetectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationNotificationListenerTest {

        @Mock
        private IdentityService identityService;

        @Mock
        private NotificationDispatchService dispatchService;

        private ViolationNotificationListener listener;

        @BeforeEach
        void setUp() {
                listener = new ViolationNotificationListener(identityService, dispatchService);
        }

        @Test
        void onViolationDetected_noHostAdmins_noDispatch() {
                UUID tenantId = UUID.randomUUID();
                UUID attemptPublicId = UUID.randomUUID();
                UUID sessionPublicId = UUID.randomUUID();

                ViolationDetectedEvent event = new ViolationDetectedEvent(
                                attemptPublicId, sessionPublicId, ViolationType.TAB_SWITCH, "detail", Instant.now(),
                                tenantId);

                when(identityService.findByTenantIdAndRole(tenantId,
                                Role.valueOf(NotificationConstants.ROLE_HOST_ADMIN)))
                                .thenReturn(List.of());

                listener.onViolationDetected(event);

                verify(dispatchService, times(0)).dispatchTo(
                                NotificationType.VIOLATION_DETECTED, null, tenantId, null, null);
        }

        @Test
        void onViolationDetected_oneHostAdmin_dispatchesOnce() {
                UUID tenantId = UUID.randomUUID();
                UUID attemptPublicId = UUID.randomUUID();
                UUID sessionPublicId = UUID.randomUUID();
                String violationDetail = "user switched tabs";

                User hostAdmin = new User();
                hostAdmin.setPublicId(UUID.randomUUID());
                hostAdmin.setEmail("admin@example.com");

                ViolationDetectedEvent event = new ViolationDetectedEvent(
                                attemptPublicId, sessionPublicId, ViolationType.TAB_SWITCH, violationDetail,
                                Instant.now(), tenantId);

                when(identityService.findByTenantIdAndRole(tenantId,
                                Role.valueOf(NotificationConstants.ROLE_HOST_ADMIN)))
                                .thenReturn(List.of(hostAdmin));

                listener.onViolationDetected(event);

                String expectedSubject = "Violation flagged: " + ViolationType.TAB_SWITCH;
                String expectedBody = "A proctor flagged attempt " + attemptPublicId + " in session " + sessionPublicId
                                + " for " + ViolationType.TAB_SWITCH + " — " + violationDetail + ".";

                verify(dispatchService).dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin, tenantId,
                                expectedSubject, expectedBody);
        }

        @Test
        void onViolationDetected_threeHostAdmins_dispatchesToEach() {
                UUID tenantId = UUID.randomUUID();
                UUID attemptPublicId = UUID.randomUUID();
                UUID sessionPublicId = UUID.randomUUID();
                String violationDetail = "suspicious audio detected";

                User hostAdmin1 = new User();
                hostAdmin1.setPublicId(UUID.randomUUID());
                hostAdmin1.setEmail("admin1@example.com");

                User hostAdmin2 = new User();
                hostAdmin2.setPublicId(UUID.randomUUID());
                hostAdmin2.setEmail("admin2@example.com");

                User hostAdmin3 = new User();
                hostAdmin3.setPublicId(UUID.randomUUID());
                hostAdmin3.setEmail("admin3@example.com");

                ViolationDetectedEvent event = new ViolationDetectedEvent(
                                attemptPublicId, sessionPublicId, ViolationType.SUSPICIOUS_AUDIO, violationDetail,
                                Instant.now(), tenantId);

                when(identityService.findByTenantIdAndRole(tenantId,
                                Role.valueOf(NotificationConstants.ROLE_HOST_ADMIN)))
                                .thenReturn(List.of(hostAdmin1, hostAdmin2, hostAdmin3));

                listener.onViolationDetected(event);

                String expectedSubject = "Violation flagged: " + ViolationType.SUSPICIOUS_AUDIO;
                String expectedBody = "A proctor flagged attempt " + attemptPublicId + " in session " + sessionPublicId
                                + " for " + ViolationType.SUSPICIOUS_AUDIO + " — " + violationDetail + ".";

                verify(dispatchService).dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin1, tenantId,
                                expectedSubject, expectedBody);
                verify(dispatchService).dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin2, tenantId,
                                expectedSubject, expectedBody);
                verify(dispatchService).dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin3, tenantId,
                                expectedSubject, expectedBody);
        }

        @Test
        void onViolationDetected_noDetail_bodyDoesNotIncludeDetail() {
                UUID tenantId = UUID.randomUUID();
                UUID attemptPublicId = UUID.randomUUID();
                UUID sessionPublicId = UUID.randomUUID();

                User hostAdmin = new User();
                hostAdmin.setPublicId(UUID.randomUUID());
                hostAdmin.setEmail("admin@example.com");

                ViolationDetectedEvent event = new ViolationDetectedEvent(
                                attemptPublicId, sessionPublicId, ViolationType.TECHNICAL_ISSUE, null, Instant.now(),
                                tenantId);

                when(identityService.findByTenantIdAndRole(tenantId,
                                Role.valueOf(NotificationConstants.ROLE_HOST_ADMIN)))
                                .thenReturn(List.of(hostAdmin));

                listener.onViolationDetected(event);

                String expectedSubject = "Violation flagged: " + ViolationType.TECHNICAL_ISSUE;
                String expectedBody = "A proctor flagged attempt " + attemptPublicId + " in session " + sessionPublicId
                                + " for " + ViolationType.TECHNICAL_ISSUE + ".";

                verify(dispatchService).dispatchTo(NotificationType.VIOLATION_DETECTED, hostAdmin, tenantId,
                                expectedSubject, expectedBody);
        }
}
