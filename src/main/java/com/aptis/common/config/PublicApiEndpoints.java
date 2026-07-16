package com.aptis.common.config;

import java.util.List;

import com.aptis.common.constant.ApiVersion;

public final class PublicApiEndpoints {

    private static final String AUTH_BASE = ApiVersion.V1 + "/auth";
    private static final String AUTH_LOGIN = AUTH_BASE + "/login";
    private static final String AUTH_REFRESH = AUTH_BASE + "/refresh";
    private static final String API_DOCS_PATTERN = "/v3/api-docs/**";
    private static final String SWAGGER_UI_PATTERN = "/swagger-ui/**";
    private static final String SWAGGER_UI_HTML = "/swagger-ui.html";
    // WebSocket handshake only — real authentication happens at the STOMP CONNECT frame
    // (StompAuthChannelInterceptor), not at this HTTP upgrade request.
    private static final String WEBSOCKET_ENDPOINT_PATTERN = "/ws/**";

    public static final String[] PUBLIC_ENDPOINTS = {
            AUTH_LOGIN,
            AUTH_REFRESH,
            API_DOCS_PATTERN,
            SWAGGER_UI_PATTERN,
            SWAGGER_UI_HTML,
            WEBSOCKET_ENDPOINT_PATTERN
    };

    private static final List<String> PUBLIC_ENDPOINT_LIST = List.of(PUBLIC_ENDPOINTS);

    private PublicApiEndpoints() {
    }

    public static boolean isPublicEndpoint(String requestUri) {
        return PUBLIC_ENDPOINT_LIST.stream()
                .anyMatch(publicEndpoint -> matchesEndpoint(publicEndpoint, requestUri));
    }

    private static boolean matchesEndpoint(String pattern, String requestUri) {
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return requestUri.equals(prefix) || requestUri.startsWith(prefix + "/");
        }
        return pattern.equals(requestUri);
    }
}
