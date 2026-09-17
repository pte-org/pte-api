package com.pte.billing.internal.vendor.payos;

import com.pte.billing.internal.constant.BillingConstants;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Deployment-owned PayOS credentials and transport settings. */
@Validated
@ConfigurationProperties(prefix = "billing.payos")
public class PayOsProperties {

    @NotBlank(message = BillingConstants.PAYOS_CLIENT_ID_REQUIRED)
    private String clientId;

    @NotBlank(message = BillingConstants.PAYOS_API_KEY_REQUIRED)
    private String apiKey;

    @NotBlank(message = BillingConstants.PAYOS_CHECKSUM_KEY_REQUIRED)
    private String checksumKey;

    @NotBlank(message = BillingConstants.PAYOS_BASE_URL_REQUIRED)
    private String baseUrl;

    @NotBlank(message = BillingConstants.PAYOS_RETURN_URL_REQUIRED)
    private String returnUrl;

    @NotBlank(message = BillingConstants.PAYOS_CANCEL_URL_REQUIRED)
    private String cancelUrl;

    @Min(value = 1, message = BillingConstants.PAYOS_TIMEOUT_INVALID)
    private int connectTimeoutMs = 2_000;

    @Min(value = 1, message = BillingConstants.PAYOS_TIMEOUT_INVALID)
    private int readTimeoutMs = 10_000;

    @Min(value = 1, message = BillingConstants.PAYOS_TIMEOUT_INVALID)
    private int paymentLinkTtlHours = BillingConstants.DEFAULT_PAYMENT_LINK_TTL_HOURS;

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getChecksumKey() {
        return checksumKey;
    }

    public void setChecksumKey(String checksumKey) {
        this.checksumKey = checksumKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getReturnUrl() {
        return returnUrl;
    }

    public void setReturnUrl(String returnUrl) {
        this.returnUrl = returnUrl;
    }

    public String getCancelUrl() {
        return cancelUrl;
    }

    public void setCancelUrl(String cancelUrl) {
        this.cancelUrl = cancelUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getPaymentLinkTtlHours() {
        return paymentLinkTtlHours;
    }

    public void setPaymentLinkTtlHours(int paymentLinkTtlHours) {
        this.paymentLinkTtlHours = paymentLinkTtlHours;
    }
}
