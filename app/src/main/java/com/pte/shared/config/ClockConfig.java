package com.pte.shared.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Shared injectable time source; fixed clocks replace it in scoped tests. */
@Configuration
public class ClockConfig {
    @Bean @ConditionalOnMissingBean(Clock.class)
    public Clock clock() { return Clock.systemUTC(); }
}
