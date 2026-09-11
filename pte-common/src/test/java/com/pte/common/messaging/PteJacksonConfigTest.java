package com.pte.common.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the fleet-wide {@code Instant} format pin (jackson3-objectmapper-migration
 * Phase 1) actually produces ISO-8601 output, not epoch millis — this is the "make it
 * explicit and audit-proof" contract {@link PteJacksonConfig} exists for.
 */
class PteJacksonConfigTest {

    @Test
    void customizer_pinsInstantToIso8601String() {
        JsonMapperBuilderCustomizer customizer = new PteJacksonConfig().pteInstantIso8601Customizer();
        JsonMapper.Builder builder = JsonMapper.builder();

        customizer.customize(builder);
        JsonMapper mapper = builder.build();

        String json = mapper.writeValueAsString(Instant.parse("2026-08-05T10:00:00Z"));

        assertThat(json).isEqualTo("\"2026-08-05T10:00:00Z\"");
    }
}
