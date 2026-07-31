package com.pte.admin.messaging;

import com.pte.admin.constant.AdminConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
