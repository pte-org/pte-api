package com.pte.proctor.messaging;

import com.pte.proctor.constant.ProctorConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producer-side RabbitMQ config for proctor's outbox relay (rabbitmq-outbox-migration
 * Phase 2). proctor only publishes — no listener container factory here;
 * downstream consumers declare and bind their own queue(s) to {@link
 * ProctorConstants#OUTBOX_EXCHANGE}. Both proctor aggregates (ProctorCommand,
 * ViolationEvent) share this one exchange, differentiated by routing key.
 * Publisher-confirm settings live in application.yml, applied to Spring
 * Boot's autoconfigured {@code RabbitTemplate}, which also autodetects the
 * {@link MessageConverter} bean below.
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public TopicExchange outboxExchange() {
        return new TopicExchange(ProctorConstants.OUTBOX_EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
