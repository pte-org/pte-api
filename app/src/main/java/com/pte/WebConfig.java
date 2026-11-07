package com.pte;

import com.pte.shared.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.web.client.RestClient;

/**
 * App-wide web infrastructure. Lives at the root package (like
 * {@link PteApplication}), not inside any business module — Spring Modulith
 * treats classes directly in the base package as unassigned to any module.
 */
@Configuration
public class WebConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    /**
     * RestClient.Builder is stateful and must not be shared; scope as prototype.
     * Clients should call {@code builder.build()} immediately rather than
     * keeping a reference to the same builder instance.
     */
    @Bean
    @Scope("prototype")
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
