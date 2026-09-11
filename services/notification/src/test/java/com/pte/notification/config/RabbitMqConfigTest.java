package com.pte.notification.config;

import com.pte.notification.messaging.job.EmailJob;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Jackson 3 {@code JacksonJsonMessageConverter} (jackson3-objectmapper-migration
 * Phase 3) actually round-trips {@link EmailJob} correctly — this is the one converter usage
 * in this service that's genuinely exercised at runtime: {@code NotificationDispatchService}
 * publishes via {@code rabbitTemplate.convertAndSend(..., new EmailJob(...))} and {@code
 * EmailWorker}'s {@code @RabbitListener} methods take {@code EmailJob} as a typed parameter
 * (unlike the other 4 notification consumers, which read a raw {@code Message} and deserialize
 * with their own injected {@code JsonMapper} directly, never touching this converter).
 *
 * <p>Spring AMQP's real listener pipeline resolves the "inferred type" for a typed
 * {@code @RabbitListener} parameter by calling {@code MessageProperties.setInferredArgumentType}
 * BEFORE invoking the converter (found by disassembling {@code AbstractJacksonMessageConverter
 * .convertContent} — {@code javaTypeMapper.getInferredType(properties)} reads from
 * {@code MessageProperties}, not from a conversion-hint argument). This test sets that property
 * the same way, rather than relying on the embedded {@code __TypeId__} header — which would
 * otherwise be checked against a trusted-packages allowlist (defaults to only
 * {@code java.util}/{@code java.lang}) and reject this app's own DTOs, exactly the failure this
 * test caught before {@code RabbitMqConfig} called {@code setAlwaysConvertToInferredType(true)}.
 */
class RabbitMqConfigTest {

    @Test
    void jsonMessageConverter_roundTripsEmailJob() {
        MessageConverter converter = new RabbitMqConfig().jsonMessageConverter(JsonMapper.builder().build());
        EmailJob job = new EmailJob(UUID.randomUUID(), "student@example.com", "Your report is ready",
                "Log in to view your score.");

        Message message = converter.toMessage(job, new MessageProperties());
        // What Spring AMQP's listener adapter does internally before calling fromMessage(),
        // based on EmailWorker#onEmailJob's own declared parameter type.
        message.getMessageProperties().setInferredArgumentType(EmailJob.class);
        Object roundTripped = converter.fromMessage(message);

        assertThat(roundTripped).isEqualTo(job);
    }
}
