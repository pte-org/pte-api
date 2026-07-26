package com.pte.proctor.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Plain STOMP over WebSocket (no SockJS fallback — the proctor console is a
 * controlled client, not a legacy-browser target). {@code /topic/**}
 * broadcasts to every proctor watching a session; {@code /queue/**} is the
 * per-user error/response channel ({@code @SendToUser}). Allowed origins are
 * explicit config, not {@code "*"} — JWT auth on CONNECT is the primary
 * defense, but origin restriction is cheap defense-in-depth.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor authChannelInterceptor;
    private final String[] allowedOriginPatterns;

    public WebSocketConfig(StompAuthChannelInterceptor authChannelInterceptor,
                           @Value("${proctor.ws.allowed-origin-patterns:http://localhost:*}") String[] allowedOriginPatterns) {
        this.authChannelInterceptor = authChannelInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").setAllowedOriginPatterns(allowedOriginPatterns);
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }
}
