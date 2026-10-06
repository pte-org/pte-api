package com.pte.notification.internal.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(InboxDeliveryProperties.class)
public class InboxDeliveryConfig { }
