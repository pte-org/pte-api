package com.pte.scoring.config;

import com.pte.common.security.InternalServiceAuth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The ONE guarded sync client scoring makes (host answer review only, never
 * during scoring itself) — mirrors exam-delivery's {@code InternalClientConfig}
 * pattern exactly. Carries the internal service key (ADR-003 mTLS
 * placeholder); short timeouts so a slow/down media service fails fast
 * instead of hanging a host's review request.
 */
@Configuration
public class InternalClientConfig {

    private static final int CONNECT_TIMEOUT_MS = 2_000;
    private static final int READ_TIMEOUT_MS = 3_000;

    @Bean
    public RestClient mediaInternalRestClient(
            @Value("${media.base-url:http://localhost:8090/api/media}") String baseUrl,
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
