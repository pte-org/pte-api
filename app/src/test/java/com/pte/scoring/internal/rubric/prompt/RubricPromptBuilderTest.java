package com.pte.scoring.internal.rubric.prompt;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.rubric.LevelDescriptor;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TraitDefinition;
import com.pte.scoring.rubric.TraitSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The rubric part of the system prompt is built from the catalog: every level of every AI_LLM trait, verbatim. */
class RubricPromptBuilderTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();

    @Test
    void everyLevelDescriptionOfEveryLlmTraitIsQuotedVerbatimWithItsLevel() {
        for (String taskType : List.of(ScoringConstants.TASK_TYPE_WRITE_ESSAY,
                ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT)) {
            TaskRubric task = CATALOG.find(taskType).orElseThrow();
            String section = RubricPromptBuilder.rubricSection(task);

            for (TraitDefinition trait : task.traits()) {
                if (trait.source() != TraitSource.AI_LLM) {
                    continue;
                }
                int max = trait.scale().maxFor(null);
                assertThat(section).as(taskType + " " + trait.key())
                        .contains(trait.key() + " (" + trait.displayNameEn() + "): integer 0-" + max);
                for (LevelDescriptor level : trait.levels()) {
                    assertThat(section).as(taskType + " " + trait.key() + " level " + level.level())
                            .contains(level.level() + ": " + level.text());
                }
            }
        }
    }

    @Test
    void formIsLeftToTheCodeAndItsDescriptionsAreNotSent() {
        TaskRubric essay = CATALOG.find(ScoringConstants.TASK_TYPE_WRITE_ESSAY).orElseThrow();
        String section = RubricPromptBuilder.rubricSection(essay);
        TraitDefinition form = essay.trait(RubricTraits.FORM).orElseThrow();

        for (LevelDescriptor level : form.levels()) {
            assertThat(section).doesNotContain(level.text());
        }
        assertThat(section).doesNotContain(RubricTraits.FORM + " (");
        assertThat(section).contains(RubricPromptBuilder.FORM_IS_CODED);
    }

    @Test
    void llmTraitsAreTheAiScoredTraitsInRubricOrder() {
        TaskRubric essay = CATALOG.find(ScoringConstants.TASK_TYPE_WRITE_ESSAY).orElseThrow();

        assertThat(RubricPromptBuilder.llmTraits(essay)).extracting(TraitDefinition::key).containsExactly(
                RubricTraits.CONTENT, RubricTraits.DEVELOPMENT_STRUCTURE_COHERENCE, RubricTraits.GRAMMAR,
                RubricTraits.GENERAL_LINGUISTIC_RANGE, RubricTraits.VOCABULARY_RANGE, RubricTraits.SPELLING);
    }

    @Test
    void aSpeakingRubricSectionListsOnlyContentAndSaysTheSpeechTraitsAreMeasured() {
        TaskRubric retell = CATALOG.find(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE).orElseThrow();

        String section = RubricPromptBuilder.rubricSection(retell);

        assertThat(section).contains("CONTENT (Content): integer 0-6").contains(RubricPromptBuilder.SPEECH_IS_MEASURED)
                .doesNotContain("PRONUNCIATION (").doesNotContain("ORAL_FLUENCY (")
                .doesNotContain(RubricPromptBuilder.FORM_IS_CODED);
    }
}
