package com.pte.examdelivery.config;

import com.pte.common.security.InternalServiceAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the ONLY guarded sync calls exam-delivery makes — both at
 * attempt-create, never during a live attempt (phase-05 design constraint).
 * Every request carries the internal service key (ADR-003 mTLS placeholder);
 * short timeouts so a slow/down upstream fails fast instead of hanging
 * attempt-start.
 */
@Configuration
public class InternalClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    @Bean
    public RestClient schedulingRestClient(
            @Value("${scheduling.base-url:http://localhost:8084/api/scheduling}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey) {
        return buildClient(baseUrl, serviceKey);
    }

    @Bean
    public RestClient authoringInternalRestClient(
            @Value("${authoring.base-url:http://localhost:8083/api/authoring}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey) {
        return buildClient(baseUrl, serviceKey);
    }

    @Bean
    public RestClient mediaInternalRestClient(
            @Value("${media.base-url:http://localhost:8090/api/media}") String baseUrl,
            @Value("${internal.service-key}") String serviceKey) {
        return buildClient(baseUrl, serviceKey);
    }

    private RestClient buildClient(String baseUrl, String serviceKey) {
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
