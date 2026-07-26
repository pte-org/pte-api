package com.pte.proctor.config;

import com.pte.common.security.InternalServiceAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP client for the ONLY guarded sync call proctor makes — to scheduling, at
 * ProctorSession-open only. Short timeouts so a slow/down scheduling fails
 * fast rather than hanging session-open.
 */
@Configuration
public class InternalClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    @Bean
    public RestClient schedulingRestClient(
            @Value("${scheduling.base-url:http://localhost:8084/api/scheduling}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .defaultHeader(InternalServiceAuth.HEADER, serviceKey)
                .build();
    }
}
