package com.pte.iam.config;

import com.pte.common.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers shared web infrastructure from pte-common (not component-scanned).
 */
@Configuration
public class WebConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
