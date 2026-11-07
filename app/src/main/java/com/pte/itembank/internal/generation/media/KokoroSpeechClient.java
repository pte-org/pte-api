package com.pte.itembank.internal.generation.media;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.llm.LlmFailureType;
import com.pte.itembank.internal.generation.llm.LlmProviderException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kokoro text-to-speech through its OpenAI-compatible {@code POST /v1/audio/speech}. Nothing is called until the
 * first {@link #synthesize}; the transcript and the key never reach a log line.
 */
@Component
public class KokoroSpeechClient {

    private static final Logger LOG = LoggerFactory.getLogger(KokoroSpeechClient.class);
    private static final String SPEECH_PATH = "/v1/audio/speech";
    private static final String MODEL = "kokoro";
    private static final String FORMAT = "wav";
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_FORBIDDEN = 403;
    private static final long MAX_RESPONSE_BYTES = 25L * 1024 * 1024;

    private final QuestionGenerationProperties.Kokoro kokoro;
    private final RestClient client;

    @Autowired
    public KokoroSpeechClient(QuestionGenerationProperties properties, RestClient.Builder builder) {
        this(properties, withTimeouts(properties.getKokoro(), builder));
    }

    /** Test seam: a ready {@link RestClient} (for example one bound to a mock server). */
    KokoroSpeechClient(QuestionGenerationProperties properties, RestClient client) {
        this.kokoro = properties.getKokoro();
        this.client = client;
    }

    private static RestClient withTimeouts(QuestionGenerationProperties.Kokoro kokoro, RestClient.Builder builder) {
        HttpClient http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(kokoro.getTimeout())
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(kokoro.getTimeout());
        return builder.requestFactory(factory).build();
    }

    /** WAV bytes for {@code text} spoken by {@code voice} at {@code speed}. */
    public byte[] synthesize(String text, String voice, double speed) {
        String base = kokoro.getBaseUrl();
        if (base == null || base.isBlank() || kokoro.getApiKey() == null || kokoro.getApiKey().isBlank()) {
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", MODEL);
        body.put("input", text);
        body.put("voice", voice);
        body.put("speed", speed);
        body.put("response_format", FORMAT);
        try {
            byte[] audio = client.post()
                    .uri(trimTrailingSlash(base) + SPEECH_PATH)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + kokoro.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (request, response) -> {
                        throw new LlmProviderException(classify(response.getStatusCode().value()));
                    })
                    .body(byte[].class);
            if (audio == null || audio.length == 0 || audio.length > MAX_RESPONSE_BYTES) {
                throw new LlmProviderException(LlmFailureType.EMPTY_RESPONSE);
            }
            return WavHeaders.normalize(audio);
        } catch (LlmProviderException failure) {
            throw failure;
        } catch (RestClientException failure) {
            LOG.warn("Kokoro call failed: errorType={}", failure.getClass().getSimpleName());
            throw new LlmProviderException(LlmFailureType.UNAVAILABLE);
        }
    }

    private static LlmFailureType classify(int status) {
        if (status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN) {
            return LlmFailureType.AUTH;
        }
        return status >= 500 || status == 429 ? LlmFailureType.UNAVAILABLE : LlmFailureType.REJECTED;
    }

    private static String trimTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
