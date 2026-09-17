package com.pte.billing.internal.vendor.payos;

import com.pte.billing.domain.Order;
import com.pte.billing.internal.constant.BillingConstants;
import com.pte.billing.internal.exception.PayOsException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Direct REST adapter for PayOS payment-link and webhook operations. */
@Component
public class PayOsClient {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String PAYMENT_REQUEST_PATH = "/v2/payment-requests";

    private final RestClient restClient;
    private final PayOsProperties properties;
    private final JsonMapper jsonMapper;

    public PayOsClient(@Qualifier("payOsRestClient") RestClient restClient,
            PayOsProperties properties, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public String createPaymentLink(Order order) {
        long amount = amountAsLong(order.getAmount());
        String description = descriptionFor(order.getOrderCode());
        Map<String, Object> request = Map.of(
                "orderCode", order.getOrderCode(),
                "amount", amount,
                "description", description,
                "cancelUrl", properties.getCancelUrl(),
                "returnUrl", properties.getReturnUrl(),
                "expiredAt", expirationEpochSecond());

        String signature = signPaymentRequest(amount, description, order.getOrderCode());
        Map<String, Object> signedRequest = new TreeMap<>(request);
        signedRequest.put("signature", signature);

        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(PAYMENT_REQUEST_PATH)
                    .header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(signedRequest)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new PayOsException(BillingConstants.PAYOS_REQUEST_FAILED, ex);
        }
        return checkoutUrl(responseBody, order.getOrderCode());
    }

    public boolean verifyWebhookSignature(JsonNode webhookPayload) {
        String signature = webhookPayload == null ? null : webhookPayload.path("signature").asText(null);
        return verifyWebhookSignature(webhookPayload, signature);
    }

    public boolean verifyWebhookSignature(JsonNode webhookPayload, String signature) {
        if (webhookPayload == null || !webhookPayload.path("data").isObject()) {
            return false;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        String expected = hmac(canonicalData(webhookPayload.path("data")));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII),
                signature.getBytes(StandardCharsets.US_ASCII));
    }

    /** Used by expiry/cancellation flows; no caller should treat a failed call as paid. */
    public void cancelPaymentLink(Long orderCode) {
        String responseBody;
        try {
            responseBody = restClient.post()
                    .uri(PAYMENT_REQUEST_PATH + "/{orderCode}/cancel", orderCode)
                    .header("x-client-id", properties.getClientId())
                    .header("x-api-key", properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cancellationReason", BillingConstants.PAYOS_EXPIRATION_CANCELLATION_REASON))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            throw new PayOsException(BillingConstants.PAYOS_CANCELLATION_FAILED, ex);
        }
        requireSuccess(responseBody, BillingConstants.PAYOS_CANCELLATION_FAILED);
    }

    private String signPaymentRequest(long amount, String description, Long orderCode) {
        Map<String, String> fields = Map.of(
                "amount", Long.toString(amount),
                "cancelUrl", properties.getCancelUrl(),
                "description", description,
                "orderCode", orderCode.toString(),
                "returnUrl", properties.getReturnUrl());
        return hmac(canonicalFields(fields));
    }

    private String checkoutUrl(String responseBody, Long expectedOrderCode) {
        JsonNode root = parseResponse(responseBody);
        requireSuccess(root, BillingConstants.PAYOS_RESPONSE_INVALID);
        JsonNode responseOrderCode = root.path("data").path("orderCode");
        if (!responseOrderCode.isIntegralNumber()
                || responseOrderCode.longValue() != expectedOrderCode) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID);
        }
        String checkoutUrl = root.path("data").path("checkoutUrl").asText(null);
        if (checkoutUrl == null || checkoutUrl.isBlank()) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID);
        }
        return checkoutUrl;
    }

    private void requireSuccess(String responseBody, String errorCode) {
        requireSuccess(parseResponse(responseBody), errorCode);
    }

    private void requireSuccess(JsonNode root, String errorCode) {
        if (!BillingConstants.PAYOS_PAYMENT_SUCCESS_CODE.equals(root.path("code").asText())
                || !root.path("data").isObject()) {
            throw new PayOsException(errorCode);
        }
    }

    private JsonNode parseResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID);
        }
        try {
            return jsonMapper.readTree(responseBody);
        } catch (RuntimeException ex) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID, ex);
        }
    }

    private String canonicalData(JsonNode data) {
        Map<String, String> fields = new TreeMap<>();
        data.propertyNames().forEach(name -> fields.put(name, canonicalValue(data.path(name))));
        return canonicalFields(fields);
    }

    private String canonicalFields(Map<String, String> fields) {
        List<String> pairs = new ArrayList<>();
        fields.forEach((key, value) -> pairs.add(key + "=" + value));
        return String.join("&", pairs);
    }

    private String canonicalValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isTextual()) {
            String text = value.asText();
            return "null".equals(text) || "undefined".equals(text) ? "" : text;
        }
        if (value.isBoolean() || value.isNumber()) {
            return value.asText();
        }
        return value.toString();
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getChecksumKey().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (GeneralSecurityException ex) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID, ex);
        }
    }

    private long amountAsLong(BigDecimal amount) {
        try {
            return amount.longValueExact();
        } catch (ArithmeticException ex) {
            throw new PayOsException(BillingConstants.ORDER_AMOUNT_INVALID, ex);
        }
    }

    private int expirationEpochSecond() {
        try {
            return Math.toIntExact(Instant.now().plusSeconds(properties.getPaymentLinkTtlHours() * 3_600L)
                    .getEpochSecond());
        } catch (ArithmeticException ex) {
            throw new PayOsException(BillingConstants.PAYOS_RESPONSE_INVALID, ex);
        }
    }

    private String descriptionFor(Long orderCode) {
        // PayOS documents a nine-character limit for some non-linked bank
        // accounts; keep the signed description within that limit.
        return "PT" + Long.toUnsignedString(orderCode, 36).toUpperCase(Locale.ROOT);
    }
}
