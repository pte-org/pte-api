package com.pte.scoring.internal.config;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.AzureSpeechRestClient;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Vendor clients for {@code scoring.ai.provider=azure-gemini}. Startup fails
 * listing every missing or malformed setting at once, so a deploy is not
 * fixed one restart per variable.
 */
@Configuration
@ConditionalOnProperty(name = "scoring.ai.provider", havingValue = "azure-gemini")
public class AzureGeminiProviderConfig {

    private static final URI GEMINI_BASE_URI = URI.create("https://generativelanguage.googleapis.com");
    // The region and model are spliced into the vendor URL, so only plain names pass.
    private static final Pattern AZURE_REGION = Pattern.compile("[a-z0-9]+");
    private static final Pattern GEMINI_MODEL = Pattern.compile("[A-Za-z0-9._-]+");
    // Keys travel in a header; a pasted space or newline would only surface as a 401 per answer.
    private static final Pattern API_KEY = Pattern.compile("\\p{Graph}+");

    private final AiProviderProperties properties;
    private final Clock clock;
    private final HttpClient httpClient;

    public AzureGeminiProviderConfig(AiProviderProperties properties, Clock clock) {
        validate(properties);
        this.properties = properties;
        this.clock = clock;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMs()))
                .build();
    }

    @Bean
    public AzureSpeechRestClient azureSpeechRestClient(JsonMapper jsonMapper) {
        AiProviderProperties.Azure azure = properties.getAzure();
        return new AzureSpeechRestClient(httpClient, jsonMapper,
                URI.create("https://" + azure.getSpeechRegion() + ".stt.speech.microsoft.com"),
                azure.getSpeechKey(), Duration.ofMillis(azure.getTimeoutMs()),
                ProviderGuard.standard(AzureSpeechRestClient.VENDOR, clock));
    }

    @Bean
    public AzureSpeechAssessor azureSpeechAssessor(AzureSpeechRestClient azureSpeechRestClient) {
        return new AzureSpeechAssessor(azureSpeechRestClient);
    }

    @Bean
    public GeminiRestClient geminiRestClient(JsonMapper jsonMapper) {
        AiProviderProperties.Gemini gemini = properties.getGemini();
        return new GeminiRestClient(httpClient, jsonMapper, GEMINI_BASE_URI, gemini.getApiKey(),
                gemini.getModel(), Duration.ofMillis(gemini.getTimeoutMs()),
                ProviderGuard.standard(GeminiRestClient.VENDOR, clock));
    }

    private static void validate(AiProviderProperties properties) {
        AiProviderProperties.Azure azure = properties.getAzure();
        AiProviderProperties.Gemini gemini = properties.getGemini();
        List<String> problems = new ArrayList<>();
        if (!isApiKey(azure.getSpeechKey())) {
            problems.add("scoring.ai.azure.speech-key (SCORING_AI_AZURE_SPEECH_KEY)");
        }
        if (azure.getSpeechRegion() == null || !AZURE_REGION.matcher(azure.getSpeechRegion()).matches()) {
            problems.add("scoring.ai.azure.speech-region (SCORING_AI_AZURE_SPEECH_REGION), e.g. southeastasia");
        }
        if (!isApiKey(gemini.getApiKey())) {
            problems.add("scoring.ai.gemini.api-key (SCORING_AI_GEMINI_API_KEY)");
        }
        if (gemini.getModel() == null || !GEMINI_MODEL.matcher(gemini.getModel()).matches()) {
            problems.add("scoring.ai.gemini.model (SCORING_AI_GEMINI_MODEL)");
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    ScoringConstants.AZURE_GEMINI_SETTINGS_INVALID.formatted(String.join(", ", problems)));
        }
    }

    private static boolean isApiKey(String value) {
        return value != null && API_KEY.matcher(value).matches();
    }
}
