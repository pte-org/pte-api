package com.pte.scoring.internal.vendor.gemini;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.AiProviderException;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.VendorHttp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * The scoring model behind an OpenAI-compatible {@code /chat/completions} gateway, such as Capstone. Gateways do
 * not all honour a response schema, so the schema is written into the instructions and the reply is read as JSON.
 * The key travels only in the Authorization header.
 */
public class OpenAiCompatibleStructuredClient implements StructuredModelClient {

    public static final String VENDOR = "OpenAI-compatible";
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String CONTENT_FILTER = "content_filter";
    private static final String SCHEMA_INSTRUCTION =
            "\n\nReply with one JSON object only, no other text, that follows this JSON schema:\n";

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI endpoint;
    private final String apiKey;
    private final String model;
    private final Duration timeout;
    private final ProviderGuard guard;

    public OpenAiCompatibleStructuredClient(HttpClient httpClient, JsonMapper jsonMapper, String baseUrl,
                                            String apiKey, String model, Duration timeout, ProviderGuard guard) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.endpoint = URI.create(stripTrailingSlash(baseUrl) + COMPLETIONS_PATH);
        this.apiKey = apiKey;
        this.model = model;
        this.timeout = timeout;
        this.guard = guard;
    }

    @Override
    public GeminiResponse generate(GeminiRequest request) {
        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonMapper.writeValueAsBytes(body(request))))
                .build();
        byte[] body = guard.call(() -> {
            HttpResponse<byte[]> response = VendorHttp.send(httpClient, httpRequest, VENDOR);
            if (!VendorHttp.isSuccess(response)) {
                throw VendorHttp.httpFailure(VENDOR, response);
            }
            return response.body();
        });
        return parse(VendorHttp.readJson(jsonMapper, body, VENDOR));
    }

    private Map<String, Object> body(GeminiRequest request) {
        String system = request.systemInstruction() + SCHEMA_INSTRUCTION
                + jsonMapper.writeValueAsString(request.responseSchema());
        Object user;
        if (request.images().isEmpty()) {
            user = request.userText();
        } else {
            List<Map<String, Object>> parts = new ArrayList<>();
            for (GeminiImage image : request.images()) {
                String url = "data:" + image.mimeType() + ";base64," + Base64.getEncoder().encodeToString(image.data());
                parts.add(Map.of("type", "image_url", "image_url", Map.of("url", url)));
            }
            parts.add(Map.of("type", "text", "text", request.userText()));
            user = parts;
        }
        return Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", system),
                        Map.of("role", "user", "content", user)));
    }

    GeminiResponse parse(JsonNode root) {
        JsonNode choice = root.path("choices").path(0);
        if (!choice.isObject()) {
            throw failure(AiFailureReason.PROVIDER_RESPONSE_INVALID);
        }
        if (CONTENT_FILTER.equals(choice.path("finish_reason").asText(""))) {
            throw failure(AiFailureReason.PROVIDER_CONTENT_BLOCKED);
        }
        String text = unfence(choice.path("message").path("content").asText(""));
        JsonNode content = VendorHttp.readJson(jsonMapper, text.getBytes(StandardCharsets.UTF_8), VENDOR);
        if (!content.isObject()) {
            throw failure(AiFailureReason.PROVIDER_RESPONSE_INVALID);
        }
        String served = root.path("model").asText("");
        return new GeminiResponse(content, served.isBlank() ? model : served);
    }

    /** Some models wrap the object in a ```json fence even when asked for JSON only. */
    static String unfence(String text) {
        String trimmed = text.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int start = trimmed.indexOf('\n');
        int end = trimmed.lastIndexOf("```");
        return start < 0 || end <= start ? trimmed : trimmed.substring(start + 1, end).strip();
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static AiProviderException failure(AiFailureReason reason) {
        return VendorHttp.failure(VENDOR, reason);
    }
}
