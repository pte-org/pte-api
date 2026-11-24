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

    /** Address applicants are told to write to; shown in customer-facing emails. */
    @NotBlank
    private String supportEmail = "support@pteprep.local";

    public String getSupportEmail() {
        return supportEmail;
    }

    public void setSupportEmail(String supportEmail) {
        this.supportEmail = supportEmail;
    }

    /** Sign-in page linked from the approval email; change per environment. */
    @NotBlank
    private String loginUrl = "http://localhost:3000/login";

    public String getLoginUrl() {
        return loginUrl;
    }

    public void setLoginUrl(String loginUrl) {
        this.loginUrl = loginUrl;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

}
