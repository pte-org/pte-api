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

    /** Time zone the exam times in emails are written in (students are told which one it is). */
    @NotBlank
    private String displayZone = "Asia/Ho_Chi_Minh";

    public String getDisplayZone() {
        return displayZone;
    }

    public void setDisplayZone(String displayZone) {
        this.displayZone = displayZone;
    }

    /** Base URL of the tenant web app; the approval and rejection emails link to its /login and /register pages. */
    @NotBlank
    private String tenantWebUrl = "http://localhost:3000";

    /** Base URL of the student practice web app; student welcome/credential emails link to its /sign-in page. */
    @NotBlank
    private String practiceWebUrl = "http://localhost:3014";

    public String getTenantWebUrl() {
        return tenantWebUrl;
    }

    public void setTenantWebUrl(String tenantWebUrl) {
        this.tenantWebUrl = tenantWebUrl;
    }

    public String getPracticeWebUrl() {
        return practiceWebUrl;
    }

    public void setPracticeWebUrl(String practiceWebUrl) {
        this.practiceWebUrl = practiceWebUrl;
    }

    /** Sign-in page linked from the approval and staff credential emails. */
    public String getLoginUrl() {
        return stripTrailingSlash(tenantWebUrl) + "/login";
    }

    /** Sign-in page linked from student welcome and credential emails. */
    public String getPracticeLoginUrl() {
        return stripTrailingSlash(practiceWebUrl) + "/sign-in";
    }

    /** Registration page linked from the rejection email, so an applicant can submit again. */
    public String getRegisterUrl() {
        return stripTrailingSlash(tenantWebUrl) + "/register";
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    public String getMailFrom() {
        return mailFrom;
    }

    public void setMailFrom(String mailFrom) {
        this.mailFrom = mailFrom;
    }

}
