package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptDocument.Section;
import com.pte.itembank.ScoringPromptSource;
import com.pte.itembank.domain.enums.PteTaskType;
import com.pte.itembank.internal.generation.constant.PromptVersionConstants;
import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.dto.PromptCatalogResponse;
import com.pte.itembank.internal.generation.exception.GenerationPromptNotFoundException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Describes every prompt of the AI features as structured documents for the read-only admin pages: the generator
 * prompts and the shared block (read from the prompt files), the reviewer prompt, and the scoring prompts that the
 * scoring module supplies. What each model really receives is still built by its own class; this only presents it.
 */
@Service
public class PromptDocumentService {

    private static final String REVIEW_TITLE = "Reviewer";
    private static final String PARAGRAPH_BREAK = "\n\n";

    private final PromptRegistry registry;
    private final ScoringPromptSource scoring;

    public PromptDocumentService(PromptRegistry registry, ScoringPromptSource scoring) {
        this.registry = registry;
        this.scoring = scoring;
    }

    public PromptCatalogResponse catalog() {
        List<PromptDocument.Summary> generationEntries = registry.allContents().entrySet().stream()
                .map(entry -> new PromptDocument.Summary(QuestionGenerationConstants.PROMPT_CATEGORY_GENERATION,
                        entry.getKey(), entry.getValue().title(), generationEngines(entry.getKey())))
                .toList();
        return new PromptCatalogResponse(registry.getPromptVersion(), List.of(
                new PromptCatalogResponse.Category(QuestionGenerationConstants.PROMPT_CATEGORY_GENERATION,
                        generationEntries),
                new PromptCatalogResponse.Category(QuestionGenerationConstants.PROMPT_CATEGORY_REVIEW,
                        List.of(reviewSummary())),
                new PromptCatalogResponse.Category(QuestionGenerationConstants.PROMPT_CATEGORY_SCORING,
                        scoring.summaries()),
                new PromptCatalogResponse.Category(QuestionGenerationConstants.PROMPT_CATEGORY_SHARED,
                        List.of(commonSummary())),
                new PromptCatalogResponse.Category(PromptVersionConstants.CATEGORY_CHATBOT, chatbotEntries())));
    }

    public PromptDocument document(String category, String key) {
        return switch (category) {
            case QuestionGenerationConstants.PROMPT_CATEGORY_GENERATION -> generationDocument(key);
            case QuestionGenerationConstants.PROMPT_CATEGORY_SCORING -> scoring.find(key)
                    .orElseThrow(GenerationPromptNotFoundException::new);
            case QuestionGenerationConstants.PROMPT_CATEGORY_SHARED -> sharedDocument(key);
            default -> throw new GenerationPromptNotFoundException();
        };
    }

    private PromptDocument generationDocument(String key) {
        PromptContent content = registry.generatorContent(key).orElseThrow(GenerationPromptNotFoundException::new);
        return new PromptDocument(QuestionGenerationConstants.PROMPT_CATEGORY_GENERATION, key, content.title(),
                sectionsOf(content), registry.getCommonPrompt() + PARAGRAPH_BREAK + registry.getPrompt(key));
    }

    private PromptDocument sharedDocument(String key) {
        if (!QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR.equals(key)) {
            throw new GenerationPromptNotFoundException();
        }
        PromptContent common = registry.commonContent().orElseThrow(GenerationPromptNotFoundException::new);
        return new PromptDocument(QuestionGenerationConstants.PROMPT_CATEGORY_SHARED, key, common.title(),
                sectionsOf(common), registry.getCommonPrompt());
    }

    /** The stored prompt as the sections the admin page draws: its intro first, then its sections, then its tokens. */
    private static List<Section> sectionsOf(PromptContent content) {
        List<Section> sections = new ArrayList<>();
        if (!content.intro().isEmpty()) {
            sections.add(new Section(QuestionGenerationConstants.PROMPT_SECTION_INTRO_ID,
                    QuestionGenerationConstants.PROMPT_SECTION_INTRO_TITLE, false, content.intro()));
        }
        content.sections().forEach(section ->
                sections.add(new Section(section.id(), section.title(), false, section.blocks())));
        List<String> tokens = PromptTextAssembler.placeholdersOf(PromptTextAssembler.assemble(content));
        if (!tokens.isEmpty()) {
            sections.add(new Section(QuestionGenerationConstants.PROMPT_SECTION_PLACEHOLDERS_ID,
                    QuestionGenerationConstants.PROMPT_SECTION_PLACEHOLDERS_TITLE, false,
                    List.of(Block.list("", tokens))));
        }
        return sections;
    }

    /** The language model writes every item; a spoken or chart task also needs a voice or a chart renderer. */
    private static List<String> generationEngines(String taskType) {
        List<String> engines = new ArrayList<>(List.of(QuestionGenerationConstants.ENGINE_LLM));
        Arrays.stream(PteTaskType.values()).filter(type -> type.name().equals(taskType)).findFirst()
                .ifPresent(type -> {
                    if (type.requiresAudioPrompt()) {
                        engines.add(QuestionGenerationConstants.ENGINE_TTS);
                    }
                    if (type.requiresImagePrompt()) {
                        engines.add(QuestionGenerationConstants.ENGINE_CHART);
                    }
                });
        return engines;
    }

    private List<PromptDocument.Summary> chatbotEntries() {
        return registry.activeTitles(AiPromptKind.CHATBOT).entrySet().stream()
                .map(entry -> new PromptDocument.Summary(PromptVersionConstants.CATEGORY_CHATBOT, entry.getKey(),
                        entry.getValue(), List.of(QuestionGenerationConstants.ENGINE_LLM)))
                .toList();
    }

    private static PromptDocument.Summary reviewSummary() {
        return new PromptDocument.Summary(QuestionGenerationConstants.PROMPT_CATEGORY_REVIEW,
                QuestionGenerationConstants.PROMPT_KEY_REVIEWER, REVIEW_TITLE,
                List.of(QuestionGenerationConstants.ENGINE_LLM));
    }

    private PromptDocument.Summary commonSummary() {
        String title = registry.commonContent().map(PromptContent::title)
                .orElse(QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR);
        return new PromptDocument.Summary(QuestionGenerationConstants.PROMPT_CATEGORY_SHARED,
                QuestionGenerationConstants.PROMPT_KEY_COMMON_GENERATOR, title,
                List.of(QuestionGenerationConstants.ENGINE_LLM));
    }
}
