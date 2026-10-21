package com.pte.scoring.internal.vendor.azure;

import com.pte.scoring.domain.enums.AiFailureReason;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.VendorHttp;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Azure Speech short-audio REST: one request per WAV of at most ~30 s.
 * The subscription key travels only in a header, never on the URL, so it
 * cannot leak through access logs or exception messages.
 */
public class AzureSpeechRestClient {

    public static final String VENDOR = "Azure Speech";
    private static final String RECOGNITION_PATH =
            "/speech/recognition/conversation/cognitiveservices/v1?language=en-US&format=detailed";
    private static final String AUDIO_CONTENT_TYPE = "audio/wav; codecs=audio/pcm; samplerate=16000";

    private final HttpClient httpClient;
    private final JsonMapper jsonMapper;
    private final URI endpoint;
    private final String subscriptionKey;
    private final Duration timeout;
    private final ProviderGuard guard;

    public AzureSpeechRestClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri,
                                 String subscriptionKey, Duration timeout) {
        this(httpClient, jsonMapper, baseUri, subscriptionKey, timeout, ProviderGuard.passThrough(VENDOR));
    }

    public AzureSpeechRestClient(HttpClient httpClient, JsonMapper jsonMapper, URI baseUri,
                                 String subscriptionKey, Duration timeout, ProviderGuard guard) {
        this.httpClient = httpClient;
        this.jsonMapper = jsonMapper;
        this.endpoint = baseUri.resolve(RECOGNITION_PATH);
        this.subscriptionKey = subscriptionKey;
        this.timeout = timeout;
        this.guard = guard;
    }

    /** Pronunciation assessment; an empty reference means unscripted speech. */
    public SpeechSegmentResult assess(byte[] wav, String referenceText) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("ReferenceText", referenceText == null ? "" : referenceText);
        config.put("GradingSystem", "HundredMark");
        config.put("Granularity", "Word");
        config.put("Dimension", "Comprehensive");
        config.put("EnableMiscue", false);
        config.put("EnableProsodyAssessment", true);
        String header = Base64.getEncoder().encodeToString(jsonMapper.writeValueAsBytes(config));
        return parse(post(wav, header), true);
    }

    /** Transcript only, no scoring. */
    public SpeechSegmentResult recognize(byte[] wav) {
        return parse(post(wav, null), false);
    }

    private byte[] post(byte[] wav, String pronunciationAssessment) {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Ocp-Apim-Subscription-Key", subscriptionKey)
                .header("Content-Type", AUDIO_CONTENT_TYPE)
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofByteArray(wav));
        if (pronunciationAssessment != null) {
            request.header("Pronunciation-Assessment", pronunciationAssessment);
        }
        HttpRequest built = request.build();
        return guard.call(() -> {
            HttpResponse<byte[]> response = VendorHttp.send(httpClient, built, VENDOR);
            if (!VendorHttp.isSuccess(response)) {
                throw VendorHttp.httpFailure(VENDOR, response);
            }
            return response.body();
        });
    }

    private SpeechSegmentResult parse(byte[] body, boolean assessed) {
        JsonNode root = VendorHttp.readJson(jsonMapper, body, VENDOR);
        return switch (root.path("RecognitionStatus").asText("")) {
            case "Success" -> success(root, assessed);
            case "NoMatch", "InitialSilenceTimeout", "BabbleTimeout" -> SpeechSegmentResult.noSpeech(assessed);
            case "Error" -> throw VendorHttp.failure(VENDOR, AiFailureReason.PROVIDER_UNAVAILABLE);
            default -> throw invalidResponse();
        };
    }

    private SpeechSegmentResult success(JsonNode root, boolean assessed) {
        JsonNode best = root.path("NBest").path(0);
        if (!best.isObject()) {
            throw invalidResponse();
        }
        PronunciationScores scores = null;
        if (assessed) {
            JsonNode assessment = assessmentOf(best);
            scores = new PronunciationScores(
                    number(assessment, "AccuracyScore"),
                    number(assessment, "FluencyScore"),
                    number(assessment, "CompletenessScore"),
                    number(assessment, "ProsodyScore"),
                    number(assessment, "PronScore"));
            if (scores.accuracy() == null) {
                throw invalidResponse();
            }
        }
        return new SpeechSegmentResult(true, best.path("Display").asText(""), best.path("Lexical").asText(""),
                root.path("Offset").asLong(0), root.path("Duration").asLong(0), scores, words(best));
    }

    private static List<SpeechWord> words(JsonNode best) {
        List<SpeechWord> words = new ArrayList<>();
        for (JsonNode word : best.path("Words")) {
            JsonNode assessment = assessmentOf(word);
            JsonNode errorType = assessment.path("ErrorType");
            words.add(new SpeechWord(word.path("Word").asText(""), word.path("Offset").asLong(0),
                    word.path("Duration").asLong(0), number(assessment, "AccuracyScore"),
                    errorType.isTextual() ? errorType.asText() : null));
        }
        return words;
    }

    /** Older API versions put the scores flat on the node; newer ones nest them. */
    private static JsonNode assessmentOf(JsonNode node) {
        return node.has("PronunciationAssessment") ? node.path("PronunciationAssessment") : node;
    }

    private static Double number(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private static RuntimeException invalidResponse() {
        return VendorHttp.failure(VENDOR, AiFailureReason.PROVIDER_RESPONSE_INVALID);
    }
}
