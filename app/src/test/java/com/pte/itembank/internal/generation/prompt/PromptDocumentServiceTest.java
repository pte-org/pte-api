package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.GenerationRubricTraits;
import com.pte.itembank.PromptDocument;
import com.pte.itembank.ScoringPromptSource;
import com.pte.itembank.internal.generation.dto.PromptCatalogResponse;
import com.pte.itembank.internal.generation.exception.GenerationPromptNotFoundException;
import com.pte.itembank.internal.generation.review.ReviewPromptBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptDocumentServiceTest {

    private static final PromptDocument.Summary SCORING_ENTRY = new PromptDocument.Summary("SCORING", "WRITE_ESSAY",
            "WRITE_ESSAY", List.of("GEMINI", "CODE"));
    private static final PromptDocument SCORING_DOCUMENT = new PromptDocument("SCORING", "WRITE_ESSAY", "WRITE_ESSAY",
            List.of(), "scoring text");

    private final PromptRegistry registry = PromptTestRegistry.create();
    private final ScoringPromptSource scoring = mock(ScoringPromptSource.class);
    private final GenerationRubricTraits traits = mock(GenerationRubricTraits.class);
    private final PromptDocumentService service;

    PromptDocumentServiceTest() {
        when(scoring.summaries()).thenReturn(List.of(SCORING_ENTRY));
        when(scoring.find("WRITE_ESSAY")).thenReturn(Optional.of(SCORING_DOCUMENT));
        when(scoring.find("NOPE")).thenReturn(Optional.empty());
        when(traits.find(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        service = new PromptDocumentService(registry, scoring);
    }

    @Test
    void theCatalogListsFiveCategoriesWithEveryGeneratorPrompt() {
        PromptCatalogResponse catalog = service.catalog();

        assertThat(catalog.categories()).extracting(PromptCatalogResponse.Category::category)
                .containsExactly("GENERATION", "REVIEW", "SCORING", "SHARED", "CHATBOT");
        assertThat(catalog.categories().get(0).entries()).hasSize(registry.all().size());
        assertThat(catalog.categories().get(2).entries()).containsExactly(SCORING_ENTRY);
        assertThat(catalog.promptVersion()).isEqualTo(registry.getPromptVersion());
    }

    @Test
    void everyPromptNamesWhoHandlesItAndSpokenOrChartTasksAddTheirRenderer() {
        PromptCatalogResponse catalog = service.catalog();

        List<PromptDocument.Summary> generation = catalog.categories().get(0).entries();
        assertThat(generation).allSatisfy(entry -> assertThat(entry.tags()).startsWith("LLM"));
        assertThat(generation).filteredOn(entry -> entry.key().equals("READ_ALOUD")).singleElement()
                .satisfies(entry -> assertThat(entry.tags()).containsExactly("LLM"));
        assertThat(generation).filteredOn(entry -> entry.key().equals("REPEAT_SENTENCE")).singleElement()
                .satisfies(entry -> assertThat(entry.tags()).containsExactly("LLM", "TTS"));
        assertThat(generation).filteredOn(entry -> entry.key().equals("DESCRIBE_IMAGE")).singleElement()
                .satisfies(entry -> assertThat(entry.tags()).containsExactly("LLM", "CHART"));
        assertThat(catalog.categories().get(1).entries().get(0).tags()).containsExactly("LLM");
        assertThat(catalog.categories().get(3).entries().get(0).tags()).containsExactly("LLM");
    }

    @Test
    void aGeneratorDocumentCarriesTheSectionsAndTheTextTheModelReceives() {
        PromptDocument document = service.document("GENERATION", "ANSWER_SHORT_QUESTION");

        assertThat(document.sections()).isNotEmpty();
        assertThat(document.assembledText())
                .startsWith(registry.getCommonPrompt())
                .endsWith(registry.getPrompt("ANSWER_SHORT_QUESTION"));
    }

    @Test
    void theSharedBlockAndTheScoringPromptAreServedAndAnUnknownOneIsNotFound() {
        assertThat(service.document("SHARED", "COMMON_GENERATOR").assembledText())
                .isEqualTo(registry.getCommonPrompt());
        assertThat(service.document("SCORING", "WRITE_ESSAY")).isSameAs(SCORING_DOCUMENT);

        assertThatThrownBy(() -> service.document("SCORING", "NOPE"))
                .isInstanceOf(GenerationPromptNotFoundException.class);
        assertThatThrownBy(() -> service.document("GENERATION", "NOPE"))
                .isInstanceOf(GenerationPromptNotFoundException.class);
        assertThatThrownBy(() -> service.document("REVIEW", "NOPE"))
                .isInstanceOf(GenerationPromptNotFoundException.class);
        assertThatThrownBy(() -> service.document("SHARED", "NOPE"))
                .isInstanceOf(GenerationPromptNotFoundException.class);
        assertThatThrownBy(() -> service.document("OTHER", "X"))
                .isInstanceOf(GenerationPromptNotFoundException.class);
    }
}
