package com.pte.identity.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PasswordResetProperties.class, WebAppProperties.class})
public class PasswordResetConfig {
}
