package com.pte.scoring.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * HTTP clients for the opt-in AI provider and downloading a presigned media
 * URL's bytes — genuine external I/O (vendor API, object storage), unlike
 * the in-process {@code MediaService} call that resolves the presigned URL
 * itself.
 */
@Configuration
@EnableConfigurationProperties(AiProviderProperties.class)
public class AiProviderConfig {

    @Bean(name = "aiProviderRestClient")
    public RestClient aiProviderRestClient(AiProviderProperties properties) {
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory(properties));
        if (properties.getApiKey() != null && !properties.getApiKey().isBlank()) {
            builder.defaultHeaders(headers -> headers.setBearerAuth(properties.getApiKey()));
        }
        return builder.build();
    }

    @Bean(name = "aiMediaDownloadRestClient")
    public RestClient aiMediaDownloadRestClient(AiProviderProperties properties) {
        return RestClient.builder()
                .requestFactory(requestFactory(properties))
                .build();
    }

    private SimpleClientHttpRequestFactory requestFactory(AiProviderProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getConnectTimeoutMs());
        factory.setReadTimeout(properties.getReadTimeoutMs());
        return factory;
    }
}
