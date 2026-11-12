package com.pte.itembank.internal.generation.prompt;

import com.pte.itembank.internal.generation.domain.AiPromptKind;
import com.pte.itembank.internal.generation.domain.AiPromptStatus;
import com.pte.itembank.internal.generation.domain.AiPromptVersion;
import com.pte.itembank.internal.generation.repository.AiPromptVersionRepository;
import com.pte.itembank.PromptDocument;
import com.pte.itembank.ScoringPromptSource;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromptSeedTest {

    private static final int GENERATOR_PROMPTS = 23;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-08T00:00:00Z"), ZoneOffset.UTC);

    private final PromptContentCodec codec = PromptTestRegistry.codec();
    private final ScoringPromptSource scoringSource = mock(ScoringPromptSource.class);

    @Test
    void everyShippedPromptIsReadableAndHasItsPlaceholdersAndJsonExample() {
        List<AiPromptVersion> versions = PromptTestRegistry.seededVersions();

        assertThat(versions).filteredOn(version -> version.getKind() == AiPromptKind.GENERATOR)
                .hasSize(GENERATOR_PROMPTS);
        assertThat(versions).filteredOn(version -> version.getKind() == AiPromptKind.SHARED).hasSize(1);
        assertThat(versions).allSatisfy(version -> {
            PromptContent content = codec.read(version.getContent());
            assertThat(content.title()).isNotBlank();
            assertThat(PromptTextAssembler.assemble(content)).isNotBlank();
            assertThat(version.getStatus()).isEqualTo(AiPromptStatus.ACTIVE);
            assertThat(version.getContentHash()).isEqualTo(codec.hash(content));
        });
    }

    @Test
    void theSharedBlockKeepsTheTokensTheGeneratorFillsIn() {
        String text = PromptTextAssembler.assemble(codec.read(
                PromptTestRegistry.find(AiPromptKind.SHARED, "COMMON_GENERATOR").orElseThrow().getContent()));

        assertThat(PromptTextAssembler.placeholdersOf(text))
                .containsExactly("{cefr}", "{topicHint}", "{avoidTopics}", "{lengthRules}");
    }

    @Test
    void theSameContentAlwaysGivesTheSameHash() {
        PromptContent content = codec.read(PromptTestRegistry.seededVersions().get(0).getContent());

        assertThat(codec.hash(content)).isEqualTo(codec.hash(codec.read(codec.write(content))));
    }

    @Test
    void theSeederAddsEveryMissingPromptOnceAndLeavesExistingOnesAlone() throws IOException {
        AiPromptVersionRepository repository = mock(AiPromptVersionRepository.class);
        PromptSeeder seeder = new PromptSeeder(repository, codec, scoringSource, CLOCK, mock(PromptRequirementUpgrader.class));

        assertThat(seeder.seedMissing()).isEqualTo(GENERATOR_PROMPTS + 4);
        ArgumentCaptor<AiPromptVersion> saved = ArgumentCaptor.forClass(AiPromptVersion.class);
        verify(repository, times(GENERATOR_PROMPTS + 4)).save(saved.capture());
        assertThat(saved.getAllValues()).allSatisfy(version -> {
            assertThat(version.getVersionNo()).isEqualTo(1);
            assertThat(version.getActivatedAt()).isEqualTo(CLOCK.instant());
        });

        when(repository.existsByKindAndPromptKey(any(), any())).thenReturn(true);
        PromptSeeder again = new PromptSeeder(repository, codec, scoringSource, CLOCK, mock(PromptRequirementUpgrader.class));
        org.mockito.Mockito.clearInvocations(repository);
        assertThat(again.seedMissing()).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void anAiScoredTaskTypeGetsItsCriteriaAsBulletsAndOthersGetNone() throws IOException {
        AiPromptVersionRepository repository = mock(AiPromptVersionRepository.class);
        PromptDocument essay = new PromptDocument("SCORING", "WRITE_ESSAY", "WRITE_ESSAY", List.of(
                new PromptDocument.Section("role", "Role sent to the AI", false,
                        List.of(PromptDocument.Block.text("", "You grade essays."))),
                new PromptDocument.Section("levels", "Level descriptions sent to the AI", false, List.of(
                        PromptDocument.Block.levels("Grammar", List.of(new PromptDocument.LevelRow(2, "Correct."),
                                new PromptDocument.LevelRow(1, "Some errors.")), true))),
                new PromptDocument.Section("reference", "Reference only", false, List.of(
                        PromptDocument.Block.levels("Form", List.of(new PromptDocument.LevelRow(2, "Fine.")),
                                false)))), "You grade essays.");
        when(scoringSource.summaries()).thenReturn(List.of(
                new PromptDocument.Summary("SCORING", "WRITE_ESSAY", "WRITE_ESSAY", List.of("GEMINI")),
                new PromptDocument.Summary("SCORING", "READ_ALOUD", "READ_ALOUD", List.of("CODE"))));
        when(scoringSource.find("WRITE_ESSAY")).thenReturn(java.util.Optional.of(essay));
        when(scoringSource.find("READ_ALOUD")).thenReturn(java.util.Optional.of(new PromptDocument("SCORING",
                "READ_ALOUD", "READ_ALOUD", List.of(), "")));

        new PromptSeeder(repository, codec, scoringSource, CLOCK, mock(PromptRequirementUpgrader.class)).seedMissing();

        ArgumentCaptor<AiPromptVersion> saved = ArgumentCaptor.forClass(AiPromptVersion.class);
        verify(repository, times(GENERATOR_PROMPTS + 5)).save(saved.capture());
        AiPromptVersion scorer = saved.getAllValues().stream()
                .filter(version -> version.getKind() == AiPromptKind.SCORER).findFirst().orElseThrow();
        PromptContent content = codec.read(scorer.getContent());
        assertThat(scorer.getPromptKey()).isEqualTo("WRITE_ESSAY");
        assertThat(content.sections()).extracting(PromptContent.Section::title)
                .containsExactly("Role", "Scoring criteria");
        assertThat(PromptTextAssembler.assembleWithoutTitle(content))
                .contains("Grammar" + System.lineSeparator().replace("\r", "") + "- 2: Correct.")
                .contains("- 1: Some errors.").doesNotContain("Form");
    }
}
