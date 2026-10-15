package com.pte.notification.internal.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment-owned sender identity and notification transport settings. */
@Validated
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    @NotBlank
    private String mailFrom = "no-reply@pteprep.local";

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

}
