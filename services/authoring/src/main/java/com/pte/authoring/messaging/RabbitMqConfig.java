package com.pte.authoring.messaging;

import com.pte.authoring.constant.AuthoringConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * Producer-side RabbitMQ config for authoring's outbox relay (rabbitmq-outbox-migration
 * Phase 2). authoring only publishes — no listener container factory here;
 * downstream consumers declare and bind their own queue(s) to {@link
 * AuthoringConstants#OUTBOX_EXCHANGE}. Publisher-confirm settings live in
 * application.yml, applied to Spring Boot's autoconfigured {@code RabbitTemplate},
 * which also autodetects the {@link MessageConverter} bean below.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(AuthoringConstants.OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // See admin's RabbitMqConfig for why: default-rejects typed POJO @RabbitListener
        // params otherwise. Set uniformly across all 9 RabbitMqConfig beans.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
