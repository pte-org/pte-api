package com.pte.identity.internal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Public base URLs of the web apps, used to build links that are mailed out. */
@ConfigurationProperties(prefix = "app.web")
public class WebAppProperties {

    private String tenantUrl = "http://localhost:3000";
    private String adminUrl = "http://localhost:3001";
    /** Where students sign in; they never reach the organization portal. */
    private String practiceUrl = "http://localhost:3014";

    public String getTenantUrl() {
        return tenantUrl;
    }

    public void setTenantUrl(String tenantUrl) {
        this.tenantUrl = tenantUrl;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    public String getPracticeUrl() {
        return practiceUrl;
    }

    public void setPracticeUrl(String practiceUrl) {
        this.practiceUrl = practiceUrl;
    }
}
