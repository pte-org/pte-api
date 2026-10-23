package com.pte.scoring.internal.config;

import com.pte.scoring.PracticeAiProperties;
import com.pte.scoring.PracticeExplanationGenerator;
import com.pte.scoring.internal.vendor.ProviderGuard;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.internal.vendor.gemini.PracticeGeminiExplanationGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.regex.Pattern;

/** Opt-in practice explanation adapter, independent from exam scoring provider selection. */
@Configuration
@ConditionalOnProperty(name = "practice.ai.enabled", havingValue = "true")
@EnableConfigurationProperties(PracticeAiProperties.class)
public class PracticeGeminiConfig {

    private static final URI GEMINI_BASE_URI = URI.create("https://generativelanguage.googleapis.com");
    private static final Pattern API_KEY = Pattern.compile("\\p{Graph}+");
    private static final Pattern MODEL = Pattern.compile("[A-Za-z0-9._-]+");

    @Bean
    public PracticeExplanationGenerator practiceExplanationGenerator(PracticeAiProperties properties,
            JsonMapper jsonMapper) {
        if (properties.getApiKey() == null || !API_KEY.matcher(properties.getApiKey()).matches()) {
            throw new IllegalStateException("practice.ai.api-key (PRACTICE_AI_GEMINI_API_KEY) is required");
        }
        if (properties.getModel() == null || !MODEL.matcher(properties.getModel()).matches()) {
            throw new IllegalStateException("practice.ai.model (PRACTICE_AI_GEMINI_MODEL) is invalid");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        GeminiRestClient client = new GeminiRestClient(httpClient, jsonMapper, GEMINI_BASE_URI,
                properties.getApiKey(), properties.getModel(), Duration.ofMillis(properties.getTimeoutMs()),
                // The durable practice job owns the retry and lease budget. Avoid nested provider retries
                // that could outlive the job lease and create duplicate external calls.
                ProviderGuard.passThrough(GeminiRestClient.VENDOR));
        return new PracticeGeminiExplanationGenerator(client, jsonMapper, properties);
    }
}
