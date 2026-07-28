package com.pte.proctor.service;

import com.pte.common.security.CurrentUser;
import com.pte.proctor.domain.enums.ProctorSessionStatus;
import com.pte.proctor.domain.exception.StompCommandForbiddenException;
import com.pte.proctor.domain.exception.StompSubscriptionForbiddenException;
import com.pte.proctor.repository.ProctorSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompDestinationAuthorizationServiceTest {

    @Test
    void assignedProctorCanSubscribeToOwnedActiveSession() {
        ProctorSessionRepository repository = mock(ProctorSessionRepository.class);
        UUID sessionId = UUID.randomUUID();
        UUID proctorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.existsBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                sessionId, proctorId, tenantId, ProctorSessionStatus.ACTIVE))
                .thenReturn(true);
        StompDestinationAuthorizationService service =
                new StompDestinationAuthorizationService(repository);

        assertDoesNotThrow(() -> service.authorizeSubscribe(
                caller(proctorId, tenantId, "PROCTOR"), topic(sessionId)));

        verify(repository).existsBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
                sessionId, proctorId, tenantId, ProctorSessionStatus.ACTIVE);
    }

    @Test
    void hostAdminCanSubscribeToActiveSessionInOwnTenant() {
        ProctorSessionRepository repository = mock(ProctorSessionRepository.class);
        UUID sessionId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(repository.existsBySessionPublicIdAndTenantIdAndStatus(
                sessionId, tenantId, ProctorSessionStatus.ACTIVE))
                .thenReturn(true);
        StompDestinationAuthorizationService service =
                new StompDestinationAuthorizationService(repository);

        assertDoesNotThrow(() -> service.authorizeSubscribe(
                caller(UUID.randomUUID(), tenantId, "HOST_ADMIN"), topic(sessionId)));

        verify(repository).existsBySessionPublicIdAndTenantIdAndStatus(
                sessionId, tenantId, ProctorSessionStatus.ACTIVE);
    }

    @Test
    void unauthorizedOrMalformedSubscriptionsUseOneDenial() {
        ProctorSessionRepository repository = mock(ProctorSessionRepository.class);
        StompDestinationAuthorizationService service =
                new StompDestinationAuthorizationService(repository);
        UUID tenantId = UUID.randomUUID();

        assertThrows(StompSubscriptionForbiddenException.class,
                () -> service.authorizeSubscribe(
                        caller(UUID.randomUUID(), tenantId, "HOST_AUTHOR"),
                        topic(UUID.randomUUID())));
        assertThrows(StompSubscriptionForbiddenException.class,
                () -> service.authorizeSubscribe(
                        caller(UUID.randomUUID(), tenantId, "HOST_ADMIN"),
                        "/topic/proctor-sessions/not-a-uuid"));
        assertThrows(StompSubscriptionForbiddenException.class,
                () -> service.authorizeSubscribe(
                        caller(UUID.randomUUID(), null, "HOST_ADMIN"),
                        topic(UUID.randomUUID())));
        assertThrows(StompSubscriptionForbiddenException.class,
                () -> service.authorizeSubscribe(
                        caller(UUID.randomUUID(), tenantId, "HOST_ADMIN"),
                        topic(UUID.randomUUID())));
    }

    @Test
    void privateUserQueuesRequireOnlyAnAuthenticatedCaller() {
        StompDestinationAuthorizationService service =
                new StompDestinationAuthorizationService(mock(ProctorSessionRepository.class));
        CurrentUser hostAuthor =
                caller(UUID.randomUUID(), UUID.randomUUID(), "HOST_AUTHOR");

        assertDoesNotThrow(() ->
                service.authorizeSubscribe(hostAuthor, "/user/queue/errors"));
        assertDoesNotThrow(() ->
                service.authorizeSubscribe(hostAuthor, "/user/queue/proctor-session"));
    }

    @Test
    void onlyProctorCanSendToKnownApplicationDestinations() {
        StompDestinationAuthorizationService service =
                new StompDestinationAuthorizationService(mock(ProctorSessionRepository.class));
        UUID tenantId = UUID.randomUUID();
        CurrentUser proctor = caller(UUID.randomUUID(), tenantId, "PROCTOR");
        CurrentUser hostAdmin = caller(UUID.randomUUID(), tenantId, "HOST_ADMIN");

        assertDoesNotThrow(() -> service.authorizeSend(
                proctor, "/app/sessions/" + UUID.randomUUID() + "/open"));
        assertDoesNotThrow(() -> service.authorizeSend(
                proctor, "/app/proctor-sessions/" + UUID.randomUUID() + "/commands"));
        assertDoesNotThrow(() -> service.authorizeSend(
                proctor, "/app/proctor-sessions/" + UUID.randomUUID() + "/violations"));
        assertThrows(StompCommandForbiddenException.class, () -> service.authorizeSend(
                hostAdmin, "/app/proctor-sessions/" + UUID.randomUUID() + "/commands"));
        assertThrows(StompCommandForbiddenException.class, () -> service.authorizeSend(
                proctor, topic(UUID.randomUUID())));
        assertThrows(StompCommandForbiddenException.class, () -> service.authorizeSend(
                proctor, "/app/proctor-sessions/not-a-uuid/commands"));
    }

    private CurrentUser caller(UUID userId, UUID tenantId, String role) {
        return new CurrentUser(userId, tenantId, List.of(role));
    }

    private String topic(UUID sessionId) {
        return "/topic/proctor-sessions/" + sessionId;
    }
}
