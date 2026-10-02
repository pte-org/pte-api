package com.pte.itembank.internal.config;

import com.pte.itembank.TaskTypeRolloutProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TaskTypeRolloutProperties.class)
public class TaskTypeRolloutConfig {
}
