package com.pte.notification.internal.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter @Setter @Validated
@ConfigurationProperties(prefix = "notification.inbox")
public class InboxDeliveryProperties {
    @Min(100) @Max(60000) private long pollDelayMs = 1000;
    @Min(1) @Max(200) private int batchSize = 50;
    @Min(1) @Max(20) private int maxAttempts = 5;
    @Min(1) @Max(3600) private long leaseSeconds = 30;
    @Min(1) @Max(3600) private long initialRetrySeconds = 5;
    @Min(1) @Max(86400) private long maxRetrySeconds = 300;
    /** Deployment enables jitter; direct fixed-clock unit fixtures can leave it at zero. */
    @Min(0) @Max(60) private long retryJitterSeconds = 0;
}
