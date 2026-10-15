package com.pte.tenancy.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Creates the bounded HTTP client used by the internal Vietnam location adapter. */
@Configuration
@EnableConfigurationProperties(VietnamLocationProperties.class)
public class VietnamLocationConfig {

    @Bean(name = "vietnamLocationRestClient")
    public RestClient vietnamLocationRestClient(VietnamLocationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMs());
        requestFactory.setReadTimeout(properties.getReadTimeoutMs());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
