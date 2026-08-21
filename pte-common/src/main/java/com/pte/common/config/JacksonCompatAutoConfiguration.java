package com.pte.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot 4's own JacksonAutoConfiguration builds a Jackson 3
 * {@code tools.jackson.databind.json.JsonMapper}, not a classic Jackson 2
 * {@link ObjectMapper}. Outbox writers across every domain service are
 * written against the Jackson 2 API, so provide the missing bean here once
 * instead of duplicating it per service. Gated on {@link ObjectMapper} being
 * on the classpath at all — reactive-only modules (e.g. gateway) pull in
 * pte-common without jackson-databind (Jackson 2) present, and would
 * otherwise fail to even load this class.
 */
@AutoConfiguration
@ConditionalOnClass(ObjectMapper.class)
public class JacksonCompatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
