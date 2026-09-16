package com.pte;

import com.pte.shared.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
