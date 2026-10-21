package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.VendorHttp;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Gemini native {@code generateContent} with a per-call response schema.
 * The API key travels only in the {@code x-goog-api-key} header, never on
 * the query string, so it cannot leak through URLs, HTTP logs or exceptions.
 */
public class GeminiRestClient {

    public static final String VENDOR = "Gemini";
    private static final Set<String> BLOCKED_FINISH_REASONS =
            Set.of("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "RECITATION");

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ProviderGuard guard;

    public GeminiRestClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri, String apiKey,
                            String model, Duration timeout) {
        this(httpClient, jsonMapper, baseUri, apiKey, model, timeout, ProviderGuard.passThrough(VENDOR));
    }

    public GeminiRestClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri, String apiKey,
                            String model, Duration timeout, ProviderGuard guard) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.endpoint = baseUri.resolve("/v1beta/models/" + model + ":generateContent");
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.guard = guard;
    }

    /** Only the HTTP exchange is retried; a 200 the model got wrong fails the same way every time. */
    public GeminiResponse generate(GeminiRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("x-goog-api-key", apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(body(request))))
                .build();
        byte[] body = guard.call(() -> {
            HttpResponse<byte[]> response = VendorHttp.send(httpClient, httpRequest, VENDOR);
            if (!VendorHttp.isSuccess(response)) {
                throw httpFailure(response);
            }
            return response.body();
        });
        return parse(VendorHttp.readJson(jsonMapper, body, VENDOR));
    }

    private static Map<String, Object> body(GeminiRequest request) {
        List<Map<String, Object>> parts = new ArrayList<>();
        for (GeminiImage image : request.images()) {
            // Jackson writes byte[] as base64, which is what inlineData.data expects.
            parts.add(Map.of("inlineData", Map.of("mimeType", image.mimeType(), "data", image.data())));
        }
        parts.add(Map.of("text", request.userText()));
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", request.systemInstruction()))),
                "contents", List.of(Map.of("role", "user", "parts", parts)),
                "generationConfig", Map.of(
                        "temperature", 0,
                        "responseMimeType", "application/json",
                        "responseSchema", request.responseSchema()));
    }

    private GeminiResponse parse(JsonNode root) {
        if (root.path("promptFeedback").hasNonNull("blockReason")) {
            throw failure(AiFailureReason.PROVIDER_CONTENT_BLOCKED);
        }
        JsonNode candidate = root.path("candidates").path(0);
        if (!candidate.isObject()) {
            throw failure(AiFailureReason.PROVIDER_RESPONSE_INVALID);
        }
        if (BLOCKED_FINISH_REASONS.contains(candidate.path("finishReason").asText(""))) {
            throw failure(AiFailureReason.PROVIDER_CONTENT_BLOCKED);
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode part : candidate.path("content").path("parts")) {
            if (!part.path("thought").asBoolean(false)) {
                text.append(part.path("text").asText(""));
            }
        }
        JsonNode content = VendorHttp.readJson(jsonMapper, text.toString().getBytes(StandardCharsets.UTF_8), VENDOR);
        if (!content.isObject()) {
            throw failure(AiFailureReason.PROVIDER_RESPONSE_INVALID);
        }
        String modelVersion = root.path("modelVersion").asText("");
        return new GeminiResponse(content, modelVersion.isBlank() ? model : modelVersion);
    }

    private AiProviderException httpFailure(HttpResponse<byte[]> response) {
        int status = response.statusCode();
        AiFailureReason reason = VendorHttp.classifyStatus(status);
        Duration retryAfter = VendorHttp.retryAfterHeader(response);
        JsonNode details = errorDetails(response.body());
        for (JsonNode detail : details) {
            if (detail.path("@type").asText("").endsWith("google.rpc.RetryInfo")) {
                retryAfter = retryDelay(detail.path("retryDelay").asText(""), retryAfter);
            }
            // Google answers a bad key with 400, not 401.
            if ("API_KEY_INVALID".equals(detail.path("reason").asText(""))) {
                reason = AiFailureReason.PROVIDER_AUTH_FAILED;
            }
        }
        return VendorHttp.httpFailure(VENDOR, reason, status, retryAfter);
    }

    private JsonNode errorDetails(byte[] body) {
        try {
            return jsonMapper.readTree(body).path("error").path("details");
        } catch (JacksonException ex) {
            return jsonMapper.createArrayNode();
        }
    }

    /** RetryInfo's protobuf duration, e.g. "17s" or "1.5s". */
    private static Duration retryDelay(String value, Duration fallback) {
        if (!value.matches("\\d{1,6}(\\.\\d{1,9})?s")) {
            return fallback;
        }
        double seconds = Double.parseDouble(value.substring(0, value.length() - 1));
        return Duration.ofMillis(Math.round(seconds * 1000));
    }

    private static AiProviderException failure(AiFailureReason reason) {
        return VendorHttp.failure(VENDOR, reason);
    }
}
