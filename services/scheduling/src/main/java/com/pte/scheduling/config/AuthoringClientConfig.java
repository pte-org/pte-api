package com.pte.scheduling.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client to authoring, used ONLY for the one guarded sync pull at
 * session-creation time (phase-04 design constraint). Short timeouts by design:
 * this call must fail fast, not hang a host request.
 */
@Configuration
public class AuthoringClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    @Bean
    public RestClient authoringRestClient(@Value("${authoring.base-url:http://localhost:8083/api/authoring}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
