package com.pte.admin.messaging;

import com.pte.admin.constant.AdminConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Producer-side RabbitMQ config for admin's outbox relay (rabbitmq-outbox-migration
 * Phase 2). admin only publishes — no listener container factory here;
 * downstream consumers declare and bind their own queue(s) to {@link
 * AdminConstants#OUTBOX_EXCHANGE}. Publisher-confirm settings
 * (spring.rabbitmq.publisher-confirm-type/publisher-returns/template.mandatory)
 * live in application.yml, applied to Spring Boot's autoconfigured {@code
 * RabbitTemplate}, which also autodetects the {@link MessageConverter} bean below.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(AdminConstants.OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // Without this, a @RabbitListener with a typed POJO parameter (e.g. this fleet's
        // EmailWorker/AiScoringWorker) rejects the message: by default this converter only
        // trusts java.util/java.lang in the embedded type-id header, even when the listener's
        // own declared parameter type is already known. Pre-existing gap with
        // Jackson2JsonMessageConverter too (same default), not new to this migration — set
        // uniformly across all 9 RabbitMqConfig beans for consistency, even where this
        // service's own listeners don't currently need it.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
