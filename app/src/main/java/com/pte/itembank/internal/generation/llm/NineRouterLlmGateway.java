package com.pte.itembank.internal.generation.llm;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.live.GenerationLiveContext;
import com.pte.itembank.internal.generation.usage.GenerationUsage;
import com.pte.itembank.internal.generation.usage.GenerationUsageRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible chat transport for the 9router. It logs only the role, model, duration, token usage and an
 * outcome code: never the prompt, the response or the key. A transient failure (429, 5xx, timeout) is retried
 * once; auth failures and other 4xx are not, redirects are never followed. While an item is being made for the
 * live view, the answer is streamed and every piece of text goes to the item's {@link TokenSink}.
 */
public class NineRouterLlmGateway implements LlmGateway {

    private static final Logger LOG = LoggerFactory.getLogger(NineRouterLlmGateway.class);
    private static final String COMPLETIONS_PATH = "/chat/completions";
    private static final String IMAGES_PATH = "/images/generations";
    private static final int MAX_ATTEMPTS = 2;
    private static final int UNAUTHORIZED = 401;
    private static final int FORBIDDEN = 403;
    private static final int TOO_MANY_REQUESTS = 429;
    private static final int SERVER_ERROR = 500;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String FENCE = "```";
    private static final String NEWLINE = "\n";

    private final RestClient restClient;
    private final QuestionGenerationProperties.Llm llm;
    private final JsonMapper jsonMapper;
    private final LlmStreamReader streamReader;
    private final GenerationUsageRecorder usageRecorder;

    public NineRouterLlmGateway(RestClient restClient, QuestionGenerationProperties properties,
                                JsonMapper jsonMapper, GenerationUsageRecorder usageRecorder) {
        this.restClient = restClient;
        this.usageRecorder = usageRecorder;
        this.llm = properties.getLlm();
        this.jsonMapper = jsonMapper;
        this.streamReader = new LlmStreamReader(jsonMapper);
    }

    @Override
    public String completeJson(LlmRole role, String systemPrompt, String userPrompt) {
        String model = role == LlmRole.GENERATOR ? llm.getGeneratorModel() : llm.getReviewerModel();
        double temperature = role == LlmRole.GENERATOR ? llm.getGeneratorTemperature()
                : llm.getReviewerTemperature();
        TokenSink sink = GenerationLiveContext.sinkFor(role);
        boolean streamed = sink != TokenSink.NONE;
        String body = requestBody(model, temperature, systemPrompt, userPrompt, streamed);
        long started = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                return streamed ? postStreamed(body, role, model, started, sink) : post(body, role, model, started);
            } catch (LlmProviderException failure) {
                boolean retry = (failure.getType() == LlmFailureType.UNAVAILABLE
                        || failure.getType() == LlmFailureType.EMPTY_RESPONSE) && attempt < MAX_ATTEMPTS;
                LOG.warn("Generation provider call failed: role={}, model={}, code={}, attempt={}, retry={}",
                        role, model, failure.getMessage(), attempt, retry);
                if (!retry) {
                    throw failure;
                }
                pause();
            }
        }
    }

    /** Sent in one piece (never streamed): the image reviewer has no live text to show. */
    @Override
    public String completeJsonWithImage(LlmRole role, String systemPrompt, String userPrompt, String mimeType,
                                        byte[] image) {
        String model = role == LlmRole.GENERATOR ? llm.getGeneratorModel() : llm.getReviewerModel();
        double temperature = role == LlmRole.GENERATOR ? llm.getGeneratorTemperature()
                : llm.getReviewerTemperature();
        Object content = List.of(
                Map.of("type", "image_url", "image_url",
                        Map.of("url", "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image))),
                Map.of("type", "text", "text", userPrompt));
        String body = requestBody(model, temperature, systemPrompt, content, false);
        long started = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            try {
                return post(body, role, model, started);
            } catch (LlmProviderException failure) {
                if (failure.getType() != LlmFailureType.UNAVAILABLE || attempt >= MAX_ATTEMPTS) {
                    throw failure;
                }
                pause();
            }
        }
    }

    private String requestBody(String model, double temperature, String systemPrompt, Object userContent,
                               boolean streamed) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", temperature);
        // 0 = no cap: reasoning models spend their token allowance on thinking and can return an empty answer.
        if (llm.getMaxTokens() > 0) {
            request.put("max_tokens", llm.getMaxTokens());
        }
        // The 9router combo models stream (text/event-stream) unless told otherwise; unwatched calls read one body.
        request.put("stream", streamed);
        if (streamed) {
            // Without this a stream carries no token counts, and the AI overview could not show what was spent.
            request.put("stream_options", Map.of("include_usage", true));
        }
        request.put("response_format", Map.of("type", "json_object"));
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userContent)));
        return jsonMapper.writeValueAsString(request);
    }

    private String post(String body, LlmRole role, String model, long started) {
        String response;
        try {
            response = restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (request, reply) -> {
                        throw new LlmProviderException(LlmFailureType.REJECTED);
                    })
                    .body(String.class);
        } catch (RestClientResponseException failure) {
            // The exception message quotes the provider body, so only the status code is used.
            throw new LlmProviderException(classify(failure.getStatusCode().value()));
        } catch (RestClientException failure) {
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
        String content = extractContent(response);
        JsonNode usage = usage(response);
        LOG.info("Generation provider call ok: role={}, model={}, durationMs={}, totalTokens={}", role, model,
                (System.nanoTime() - started) / NANOS_PER_MILLI, usage.path("total_tokens").asLong(-1));
        usageRecorder.record(role.name(), model, GenerationUsage.ofCall(usage.path("prompt_tokens").asLong(-1),
                usage.path("completion_tokens").asLong(-1)));
        return content;
    }

    /** The same call, read while the model writes: each piece of text goes to the sink as it arrives. */
    private String postStreamed(String body, LlmRole role, String model, long started, TokenSink sink) {
        sink.begin();
        LlmStreamReader.Result result;
        try {
            result = restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, reply) -> {
                        if (reply.getStatusCode().is3xxRedirection()) {
                            throw new LlmProviderException(LlmFailureType.REJECTED);
                        }
                        if (reply.getStatusCode().isError()) {
                            // The body quotes the provider's message, so only the status code is used.
                            throw new LlmProviderException(classify(reply.getStatusCode().value()));
                        }
                        return streamReader.read(reply.getBody(), sink);
                    });
        } catch (RestClientException failure) {
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
        if (result == null || result.content().isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        LOG.info("Generation provider call ok: role={}, model={}, durationMs={}, totalTokens={}, streamed=true",
                role, model, (System.nanoTime() - started) / NANOS_PER_MILLI, result.totalTokens());
        usageRecorder.record(role.name(), model, GenerationUsage.ofCall(result.promptTokens(),
                result.completionTokens()));
        return stripFence(result.content().trim());
    }

    private String extractContent(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        JsonNode content;
        try {
            content = jsonMapper.readTree(response).path("choices").path(0).path("message").path("content");
        } catch (RuntimeException unparsable) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        return stripFence(content.asText().trim());
    }

    private JsonNode usage(String response) {
        try {
            return jsonMapper.readTree(response).path("usage");
        } catch (RuntimeException unparsable) {
            return jsonMapper.createObjectNode();
        }
    }

    private static String stripFence(String text) {
        if (!text.startsWith(FENCE)) {
            return text;
        }
        int firstLineEnd = text.indexOf(NEWLINE);
        int closing = text.lastIndexOf(FENCE);
        if (firstLineEnd < 0 || closing <= firstLineEnd) {
            return text;
        }
        return text.substring(firstLineEnd + 1, closing).trim();
    }

    @Override
    public byte[] generateImage(String prompt) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", llm.getImageModel());
        request.put("prompt", prompt);
        request.put("n", 1);
        request.put("size", "auto");
        request.put("output_format", "png");
        long started = System.nanoTime();
        String response;
        try {
            response = restClient.post().uri(IMAGES_PATH).contentType(MediaType.APPLICATION_JSON)
                    .body(jsonMapper.writeValueAsString(request)).retrieve()
                    .onStatus(HttpStatusCode::is3xxRedirection, (sent, reply) -> {
                        throw new LlmProviderException(LlmFailureType.REJECTED);
                    })
                    .body(String.class);
        } catch (RestClientResponseException failure) {
            throw new LlmProviderException(classify(failure.getStatusCode().value()));
        } catch (RestClientException failure) {
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
        byte[] png = decodeImage(response);
        LOG.info("Generation image call ok: model={}, durationMs={}, bytes={}", llm.getImageModel(),
                (System.nanoTime() - started) / NANOS_PER_MILLI, png.length);
        return png;
    }

    private byte[] decodeImage(String response) {
        if (response == null || response.isBlank()) {
            throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
        }
        try {
            JsonNode image = jsonMapper.readTree(response).path("data").path(0).path("b64_json");
            if (!image.isTextual() || image.asText().isBlank()) {
                throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
            }
            return Base64.getDecoder().decode(image.asText());
        } catch (IllegalArgumentException | tools.jackson.core.JacksonException unreadable) {
            throw new LlmProviderException(LlmFailureType.REJECTED);
        }
    }

    private static LlmFailureType classify(int status) {
        if (status == UNAUTHORIZED || status == FORBIDDEN) {
            return LlmFailureType.AUTH;
        }
        if (status == TOO_MANY_REQUESTS || status >= SERVER_ERROR) {
            return LlmFailureType.UNAVAILABLE;
        }
        return LlmFailureType.REJECTED;
    }

    private void pause() {
        try {
            Thread.sleep(llm.getRetryDelay().toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
    }
}
