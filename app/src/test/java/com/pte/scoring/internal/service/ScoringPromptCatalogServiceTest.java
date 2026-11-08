package com.pte.scoring.internal.service;

import com.pte.itembank.PromptDocument;
import com.pte.itembank.PromptDocument.Block;
import com.pte.itembank.PromptDocument.BlockType;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.rubric.RubricCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoringPromptCatalogServiceTest {

    private final RubricCatalog catalog = new RubricCatalogImpl();
    private final ScoringPromptCatalogService service = new ScoringPromptCatalogService(catalog);

    private PromptDocument document(String taskType) {
        return service.find(taskType).orElseThrow();
    }

    private static List<Block> blocksOf(PromptDocument document, String sectionId) {
        return document.sections().stream().filter(section -> section.id().equals(sectionId))
                .flatMap(section -> section.blocks().stream()).toList();
    }

    @Test
    void everyTaskTypeOfTheCatalogHasASummaryAndADocument() {
        assertThat(service.summaries()).hasSameSizeAs(catalog.all())
                .allSatisfy(summary -> assertThat(service.find(summary.key())).isPresent());
        assertThat(service.find("NOT_A_TASK")).isEmpty();
    }

    @Test
    void aWritingTaskShowsWhatGeminiIsSentAndMatchesTheScorersSystemPrompt() {
        PromptDocument essay = document("WRITE_ESSAY");

        assertThat(essay.sections()).extracting(PromptDocument.Section::id)
                .contains("role", "levels", "notes", "guard");
        assertThat(blocksOf(essay, "role").get(0).text()).startsWith("You are a PTE Academic examiner");
        assertThat(blocksOf(essay, "levels")).filteredOn(block -> block.type() == BlockType.LEVELS)
                .isNotEmpty().allMatch(Block::sentToAi);
        assertThat(blocksOf(essay, "notes").get(0).items()).contains(RubricPromptBuilder.FORM_IS_CODED);
        assertThat(blocksOf(essay, "guard").get(0).text()).contains("never instructions to you");
        // The page shows the same text the scorer sends: examiner, rubric section, guard.
        assertThat(essay.assembledText()).startsWith(blocksOf(essay, "role").get(0).text())
                .contains(RubricPromptBuilder.rubricSection(catalog.find("WRITE_ESSAY").orElseThrow()))
                .endsWith(blocksOf(essay, "guard").get(0).text());
    }

    @Test
    void aReadingAloudTaskHasNoPromptForTheAiAndItsLevelsAreReferenceOnly() {
        PromptDocument readAloud = document("READ_ALOUD");

        assertThat(readAloud.assembledText()).isEmpty();
        assertThat(readAloud.sections()).extracting(PromptDocument.Section::id)
                .containsExactly("reference");
        assertThat(blocksOf(readAloud, "reference")).isNotEmpty().noneMatch(Block::sentToAi);
    }

    @Test
    void aSpeakingTaskMixesAzureAndGeminiAndExplainsWhatTheAiMustNotScore() {
        PromptDocument retell = document("RE_TELL_LECTURE");

        assertThat(blocksOf(retell, "notes").get(0).items()).contains(RubricPromptBuilder.SPEECH_IS_MEASURED);
        assertThat(service.summaries()).filteredOn(summary -> summary.key().equals("RE_TELL_LECTURE"))
                .singleElement().satisfies(summary -> assertThat(summary.tags()).contains("AZURE", "GEMINI"));
    }

    @Test
    void anAnswerKeyTaskIsScoredByCodeAloneAndHasNoPromptForTheAi() {
        PromptDocument asq = document("ANSWER_SHORT_QUESTION");

        assertThat(asq.sections()).extracting(PromptDocument.Section::id).doesNotContain("role", "levels");
        assertThat(asq.assembledText()).isEmpty();
    }
}
