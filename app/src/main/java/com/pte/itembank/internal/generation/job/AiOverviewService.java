package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.dto.AiStatusResponse;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import org.springframework.stereotype.Service;

import java.net.URI;

/** Read-only view of the AI configuration for the admin pages. Never exposes a key. */
@Service
public class AiOverviewService {

    private final QuestionGenerationProperties properties;
    private final PromptRegistry prompts;
    private final GenerationTaskTypeCatalog catalog;

    public AiOverviewService(QuestionGenerationProperties properties, PromptRegistry prompts,
                             GenerationTaskTypeCatalog catalog) {
        this.properties = properties;
        this.prompts = prompts;
        this.catalog = catalog;
    }

    public AiStatusResponse status() {
        QuestionGenerationProperties.Llm llm = properties.getLlm();
        QuestionGenerationProperties.Kokoro kokoro = properties.getKokoro();
        QuestionGenerationProperties.Limits limits = properties.getLimits();
        return new AiStatusResponse(properties.isEnabled(), llm.getGeneratorModel(), llm.getReviewerModel(),
                hostOf(llm.getBaseUrl()), hasText(llm.getApiKey()), hostOf(kokoro.getBaseUrl()),
                hasText(kokoro.getApiKey()), prompts.getPromptVersion(), catalog.supported().size(),
                new AiStatusResponse.Limits(limits.getMaxItemsPerJob(), limits.getMaxItemsPerUserPerDay(),
                        limits.getMaxItemsPerDayGlobal(), limits.getMaxConcurrentJobs(), limits.getMaxCallsPerItem()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** The host of a URL, or an empty string when it is not set or not a URL. */
    private static String hostOf(String url) {
        if (!hasText(url)) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host;
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }
}
