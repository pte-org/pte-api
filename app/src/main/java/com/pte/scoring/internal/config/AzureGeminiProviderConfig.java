package com.pte.scoring.internal.config;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.azure.AzureSpeechAssessor;
import com.pte.scoring.internal.vendor.azure.AzureSpeechRestClient;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.OpenAiCompatibleStructuredClient;
import com.pte.scoring.internal.vendor.gemini.StoredPromptModelClient;
import com.pte.scoring.internal.vendor.gemini.StructuredModelClient;
import com.pte.itembank.PromptTexts;
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
                ProviderGuard.standard(AzureSpeechRestClient.VENDOR, properties.getRetryMaxAttempts(),
                properties.getBreakerThreshold(), clock));
    }

    @Bean
    public AzureSpeechAssessor azureSpeechAssessor(AzureSpeechRestClient azureSpeechRestClient) {
        return new AzureSpeechAssessor(azureSpeechRestClient);
    }

    @Bean
    public StructuredModelClient scoringModelClient(JsonMapper jsonMapper, PromptTexts prompts) {
        // Every scoring call goes out with the prompt in use on the AI prompt pages.
        return new StoredPromptModelClient(vendorModelClient(jsonMapper), prompts);
    }

    private StructuredModelClient vendorModelClient(JsonMapper jsonMapper) {
        AiProviderProperties.Gemini gemini = properties.getGemini();
        if (properties.usesGateway()) {
            ScoringGatewayProperties gateway = properties.getGateway();
            return new OpenAiCompatibleStructuredClient(httpClient, jsonMapper, gateway.getBaseUrl(),
                    gateway.getApiKey(), gateway.getModel(), Duration.ofMillis(gateway.getTimeoutMs()),
                    ProviderGuard.standard(OpenAiCompatibleStructuredClient.VENDOR, properties.getRetryMaxAttempts(),
                properties.getBreakerThreshold(), clock));
        }
        return new GeminiRestClient(httpClient, jsonMapper, GEMINI_BASE_URI, gemini.getApiKey(),
                gemini.getModel(), Duration.ofMillis(gemini.getTimeoutMs()),
                ProviderGuard.standard(GeminiRestClient.VENDOR, properties.getRetryMaxAttempts(),
                properties.getBreakerThreshold(), clock));
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
        if (properties.usesGateway()) {
            validateGateway(properties.getGateway(), problems);
        } else if (!AiProviderProperties.TEXT_VENDOR_GEMINI.equalsIgnoreCase(properties.getTextVendor())) {
            problems.add("scoring.ai.text-vendor (SCORING_AI_TEXT_VENDOR), gemini or gateway");
        } else {
            if (!isApiKey(gemini.getApiKey())) {
                problems.add("scoring.ai.gemini.api-key (SCORING_AI_GEMINI_API_KEY)");
            }
            if (gemini.getModel() == null || !GEMINI_MODEL.matcher(gemini.getModel()).matches()) {
                problems.add("scoring.ai.gemini.model (SCORING_AI_GEMINI_MODEL)");
            }
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    ScoringConstants.AZURE_GEMINI_SETTINGS_INVALID.formatted(String.join(", ", problems)));
        }
    }

    private static void validateGateway(ScoringGatewayProperties gateway, List<String> problems) {
        String baseUrl = gateway.getBaseUrl();
        if (baseUrl == null || !(baseUrl.startsWith("https://") || baseUrl.startsWith("http://"))) {
            problems.add("scoring.ai.gateway.base-url (SCORING_AI_GATEWAY_BASE_URL or AI_GATEWAY_BASE_URL)");
        }
        if (!isApiKey(gateway.getApiKey())) {
            problems.add("scoring.ai.gateway.api-key (SCORING_AI_GATEWAY_API_KEY or AI_GATEWAY_API_KEY)");
        }
        if (gateway.getModel() == null || gateway.getModel().isBlank()) {
            problems.add("scoring.ai.gateway.model (SCORING_AI_GATEWAY_MODEL or AI_GATEWAY_MODEL)");
        }
    }

    private static boolean isApiKey(String value) {
        return value != null && API_KEY.matcher(value).matches();
    }
}
