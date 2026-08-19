package com.pte.scoring.config;

import com.pte.scoring.messaging.job.AiScoringJob;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Jackson 3 {@code JacksonJsonMessageConverter} (jackson3-objectmapper-migration
 * Phase 3) actually round-trips {@link AiScoringJob} correctly — the one converter usage in this
 * service genuinely exercised at runtime: {@code AiScoringDispatcher} publishes via {@code
 * rabbitTemplate.convertAndSend(..., new AiScoringJob(...))} and {@code AiScoringWorker}'s
 * {@code @RabbitListener} methods take {@code AiScoringJob} as a typed parameter (unlike the
 * other 2 scoring consumers, which read a raw {@code Message} and deserialize with their own
 * injected {@code JsonMapper} directly, never touching this converter).
 *
 * <p>See {@code notification}'s equivalent {@code RabbitMqConfigTest} for why
 * {@code setInferredArgumentType} is set explicitly here rather than relying on the embedded
 * {@code __TypeId__} header (which is checked against a trusted-packages allowlist defaulting to
 * only {@code java.util}/{@code java.lang}, and would reject this app's own DTOs).
 */
class RabbitMqConfigTest {

    @Test
    void jsonMessageConverter_roundTripsAiScoringJob() {
        MessageConverter converter = new RabbitMqConfig().jsonMessageConverter(JsonMapper.builder().build());
        AiScoringJob job = new AiScoringJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), "READ_ALOUD", "payload text", "reference text");

        Message message = converter.toMessage(job, new MessageProperties());
        message.getMessageProperties().setInferredArgumentType(AiScoringJob.class);
        Object roundTripped = converter.fromMessage(message);

        assertThat(roundTripped).isEqualTo(job);
    }
}
