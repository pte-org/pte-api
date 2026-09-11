package com.pte.common.messaging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import tools.jackson.databind.cfg.DateTimeFeature;

/**
 * Pins {@code Instant} (de)serialization format fleet-wide, so every producer/consumer
 * pair sharing a RabbitMQ queue (jackson3-objectmapper-migration) agrees on the wire
 * format instead of each service silently inheriting whatever Jackson 3 defaults to.
 *
 * <p>Registered via {@code META-INF/spring/org.springframework.boot.autoconfigure.
 * AutoConfiguration.imports} so every service picks this up automatically just by
 * depending on {@code pte-common} — no service needs to {@code @Import} or widen its
 * {@code @ComponentScan}, since {@code pte-common} has never required either for any of
 * its other shared classes (they're plain base classes, not beans, until now).
 *
 * <p>Contributes a {@link JsonMapperBuilderCustomizer} rather than declaring a
 * replacement {@code JsonMapper} bean outright, so Spring Boot's own autoconfigured
 * {@code JsonMapper} (and everything else that builds on it, e.g. the {@code
 * JacksonJsonMessageConverter} used for RabbitMQ) still gets this one explicit
 * customization layered on top, rather than services losing any other autoconfigured
 * Jackson behavior by accident.
 */
@AutoConfiguration
public class PteJacksonConfig {

    /**
     * Pins {@code Instant} (and other date/time types) to ISO-8601 strings
     * ({@code DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS = false}) instead of epoch
     * millis. This is NOT a behavior change: Spring Boot's autoconfigured Jackson 3
     * {@code JsonMapper} already defaults to this (verified empirically — no service in
     * this repo overrides {@code spring.jackson.serialization.write-dates-as-timestamps}
     * or defines a competing mapper bean). The point of this bean is to make that fact
     * explicit and audit-proof rather than an implicit assumption every service happens
     * to share today by coincidence of Jackson's current default.
     */
    @Bean
    public JsonMapperBuilderCustomizer pteInstantIso8601Customizer() {
        return builder -> builder.configure(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
}
