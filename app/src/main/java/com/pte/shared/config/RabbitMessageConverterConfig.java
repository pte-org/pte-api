package com.pte.shared.config;

import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;

/**
 * The one {@link MessageConverter} bean for the whole app. Without an
 * explicit {@code @Primary} bean here, Spring Boot's auto-configured {@code
 * RabbitTemplate} (used by every module's publish-side call, e.g. {@code
 * AiScoringDispatcher}/{@code NotificationDispatchService}) falls back to
 * {@code SimpleMessageConverter}, which only accepts String/byte[]/
 * Serializable — not a plain record like {@code AiScoringJob}/{@code
 * EmailJob}, and throws at publish time. Each module's own {@code
 * RabbitListenerContainerFactory} (consume-side) wires this same bean in by
 * type, so producer and consumer never disagree on wire format.
 */
@Configuration
public class RabbitMessageConverterConfig {

    @Bean
    @Primary
    public MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter(jsonMapper);
        // Every @RabbitListener in this app takes a typed record parameter (AiScoringJob,
        // EmailJob) — by default this converter only trusts java.util/java.lang in the
        // embedded type-id header, even when the listener's own declared parameter type
        // is already known.
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }
}
