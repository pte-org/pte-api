package com.pte.chat.internal.config;

import com.pte.chat.internal.constant.ChatConstants;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Root of every chat bean that is not plain JPA: later phases hang the Gemini client, quota, gate and
 * controller off the same {@code chat.ai.enabled} switch. With it off none of them exist and the app
 * starts without a key; with it on, a bad setting stops startup with the full list of problems.
 */
@Configuration
@ConditionalOnProperty(name = ChatConstants.PROPERTY_ENABLED, havingValue = "true")
@EnableConfigurationProperties(ChatAiProperties.class)
public class ChatAiConfig {

    public ChatAiConfig(ChatAiProperties properties) {
        ChatAiPropertiesValidator.requireValid(properties);
    }
}
