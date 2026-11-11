package com.pte.itembank.internal.generation.job;

import com.pte.itembank.internal.generation.config.GenerationLimits;
import com.pte.itembank.internal.generation.config.QuestionGenerationProperties;
import com.pte.itembank.internal.generation.dto.AiStatusResponse;
import com.pte.itembank.internal.generation.prompt.PromptRegistry;
import org.springframework.stereotype.Service;

/** Read-only view of the AI configuration for the admin pages. Never exposes a key. */
@Service
public class AiOverviewService {

    private final QuestionGenerationProperties properties;
    private final PromptRegistry prompts;
    private final GenerationTaskTypeCatalog catalog;
    private final GenerationLimits allowance;

    public AiOverviewService(QuestionGenerationProperties properties, PromptRegistry prompts,
                             GenerationTaskTypeCatalog catalog, GenerationLimits allowance) {
        this.allowance = allowance;
        this.properties = properties;
        this.prompts = prompts;
        this.catalog = catalog;
    }

    public AiStatusResponse status() {
        QuestionGenerationProperties.Llm llm = properties.getLlm();
        QuestionGenerationProperties.Kokoro kokoro = properties.getKokoro();
        return new AiStatusResponse(properties.isEnabled(), llm.getGeneratorModel(), llm.getReviewerModel(),
                ProviderLinks.hostOf(llm.getBaseUrl()), ProviderLinks.gatewayPage(llm.getWebUrl(), llm.getBaseUrl()),
                hasText(llm.getApiKey()), ProviderLinks.hostOf(kokoro.getBaseUrl()),
                ProviderLinks.speechPage(kokoro.getWebUrl(), kokoro.getBaseUrl()),
                hasText(kokoro.getApiKey()), prompts.getPromptVersion(), catalog.supported().size(),
                new AiStatusResponse.Limits(allowance.maxItemsPerJob(), allowance.maxItemsPerUserPerDay(),
                        allowance.maxItemsPerDayGlobal(), allowance.maxConcurrentJobs(), allowance.maxCallsPerItem()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
