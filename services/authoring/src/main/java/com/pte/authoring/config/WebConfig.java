package com.pte.authoring.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pte.common.web.CorrelationIdFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebConfig {

    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }

    // Spring Boot's autoconfigured ObjectMapper here is Jackson 3
    // (tools.jackson.databind), but this module's own code is written
    // against the classic Jackson 2 API — provide that bean explicitly.
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
