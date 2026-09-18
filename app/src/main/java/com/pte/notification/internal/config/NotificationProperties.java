package com.pte.notification.internal.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment-owned sender identity and tenant-application response SLA. */
@Validated
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    @NotBlank
    private String mailFrom = "no-reply@ptehub.local";

    @Min(1)
    private int applicationResponseSlaHours = 72;

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

    public int getApplicationResponseSlaHours() {
        return applicationResponseSlaHours;
    }

    public void setApplicationResponseSlaHours(int applicationResponseSlaHours) {
        this.applicationResponseSlaHours = applicationResponseSlaHours;
    }
}
