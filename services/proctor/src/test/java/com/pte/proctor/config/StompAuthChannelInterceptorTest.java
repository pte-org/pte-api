package com.pte.proctor.config;

import com.pte.common.security.CurrentUser;
import com.pte.common.security.SecurityClaims;
import com.pte.proctor.domain.exception.StompSubscriptionForbiddenException;
import com.pte.proctor.security.StompPrincipal;
import com.pte.proctor.service.StompDestinationAuthorizationService;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StompAuthChannelInterceptorTest {

    @Test
    void connectAuthenticatesAndAttachesCurrentUserPrincipal() {
        JwtDecoder decoder = mock(JwtDecoder.class);
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(decoder.decode("valid-token")).thenReturn(jwt(userId, tenantId, List.of("PROCTOR")));
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                decoder, mock(StompDestinationAuthorizationService.class));
        Message<?> message = message(StompCommand.CONNECT, null, null, "Bearer valid-token");

        Message<?> result = interceptor.preSend(message, mock(MessageChannel.class));

        StompPrincipal principal = assertInstanceOf(
                StompPrincipal.class,
                StompHeaderAccessor.wrap(result).getUser());
        assertEquals(new CurrentUser(userId, tenantId, List.of("PROCTOR")),
                principal.currentUser());
    }

    @Test
    void subscribeAndSendDelegateUsingInheritedPrincipal() {
        StompDestinationAuthorizationService authorization =
                mock(StompDestinationAuthorizationService.class);
        StompAuthChannelInterceptor interceptor =
                new StompAuthChannelInterceptor(mock(JwtDecoder.class), authorization);
        CurrentUser caller =
                new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("PROCTOR"));
        String topic = "/topic/proctor-sessions/" + UUID.randomUUID();
        String command = "/app/proctor-sessions/" + UUID.randomUUID() + "/commands";

        interceptor.preSend(
                message(StompCommand.SUBSCRIBE, topic, new StompPrincipal(caller), null),
                mock(MessageChannel.class));
        interceptor.preSend(
                message(StompCommand.SEND, command, new StompPrincipal(caller), null),
                mock(MessageChannel.class));

        verify(authorization).authorizeSubscribe(caller, topic);
        verify(authorization).authorizeSend(caller, command);
    }

    @Test
    void subscribeWithoutInheritedPrincipalIsDenied() {
        StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
                mock(JwtDecoder.class),
                mock(StompDestinationAuthorizationService.class));

        assertThrows(StompSubscriptionForbiddenException.class, () -> interceptor.preSend(
                message(
                        StompCommand.SUBSCRIBE,
                        "/topic/proctor-sessions/" + UUID.randomUUID(),
                        null,
                        null),
                mock(MessageChannel.class)));
    }

    private Message<?> message(
            StompCommand command,
            String destination,
            StompPrincipal principal,
            String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (principal != null) {
            accessor.setUser(principal);
        }
        if (authorization != null) {
            accessor.setNativeHeader("Authorization", authorization);
        }
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Jwt jwt(UUID userId, UUID tenantId, List<String> roles) {
        return new Jwt(
                "valid-token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                Map.of(
                        "sub", userId.toString(),
                        SecurityClaims.TENANT_ID, tenantId.toString(),
                        SecurityClaims.ROLES, roles));
    }
}
