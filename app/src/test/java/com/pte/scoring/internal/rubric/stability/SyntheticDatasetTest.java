package com.pte.scoring.internal.rubric.stability;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.rubric.prompt.RubricPromptBuilder;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset.SpeakingSample;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset.TtsEntry;
import com.pte.scoring.internal.rubric.stability.SyntheticDataset.WritingSample;
import com.pte.scoring.internal.vendor.gemini.FormCheck;
import com.pte.scoring.rubric.RubricCatalog;
import com.pte.scoring.rubric.RubricTraits;
import com.pte.scoring.rubric.TaskRubric;
import com.pte.scoring.rubric.TextMetrics;
import com.pte.scoring.rubric.TraitDefinition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The synthetic dataset must agree with the rubric it is meant to probe: every intended band lies on the catalog's
 * Pearson scale, and every intended Form band is exactly what {@code FormRule.bandFor} gives for the sample's real
 * counts, so a wrong word count in a hand-written sample cannot pass as an intended band.
 */
class SyntheticDatasetTest {

    private static final RubricCatalog CATALOG = new RubricCatalogImpl();

    private final SyntheticDataset.Writing writing = SyntheticDataset.writing();
    private final SyntheticDataset.Speaking speaking = SyntheticDataset.speaking();

    @Test
    void writingHasSevenSwtSevenWeAndSixSstSamplesWithUniqueIdsAndNotes() {
        assertThat(countByTaskType(writing.samples(), WritingSample::taskType)).containsOnly(
                Map.entry(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT, 7L),
                Map.entry(ScoringConstants.TASK_TYPE_WRITE_ESSAY, 7L),
                Map.entry(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT, 6L));
        assertUnique(writing.samples().stream().map(WritingSample::id).toList());
        assertThat(writing.samples()).allSatisfy(sample -> {
            assertThat(sample.answer()).isNotBlank();
            assertThat(sample.generationNotes()).isNotBlank();
            assertThat(writing.sourceOf(sample)).isNotNull();
            assertThat(writing.sourceOf(sample).taskType()).isEqualTo(sample.taskType());
            assertThat(writing.sourceOf(sample).text()).isNotBlank();
        });
    }

    @Test
    void everyWritingSampleNamesEveryTraitOfItsRubricOnTheCatalogScale() {
        assertThat(writing.samples()).allSatisfy(sample -> {
            TaskRubric task = CATALOG.find(sample.taskType()).orElseThrow();
            assertThat(sample.intendedBands().keySet()).as(sample.id())
                    .containsExactlyInAnyOrderElementsOf(task.traits().stream().map(TraitDefinition::key).toList());
            assertOnScale(sample.id(), task, sample.intendedBands());
        });
    }

    @Test
    void everyIntendedFormBandEqualsFormRuleBandForOnTheRealCounts() {
        assertThat(writing.samples()).allSatisfy(sample -> {
            TaskRubric task = CATALOG.find(sample.taskType()).orElseThrow();
            TextMetrics metrics = FormCheck.measure(sample.answer());
            assertThat(sample.intendedBands().get(RubricTraits.FORM))
                    .as("%s: %d words, %d sentences", sample.id(), metrics.wordCount(), metrics.sentenceCount())
                    .isEqualTo(task.formRule().bandFor(metrics));
        });
    }

    /** The edge cases the phase asks for really are in the data, measured by the same counter as production. */
    @Test
    void writingCoversTheRequestedLengthAndFormEdgeCases() {
        assertThat(wordCounts(ScoringConstants.TASK_TYPE_WRITE_ESSAY)).anyMatch(words -> words < 120)
                .anyMatch(words -> words >= 120 && words <= 199).anyMatch(words -> words >= 200 && words <= 300)
                .anyMatch(words -> words >= 301 && words <= 380);
        assertThat(wordCounts(ScoringConstants.TASK_TYPE_SUMMARIZE_SPOKEN_TEXT)).anyMatch(words -> words < 40)
                .anyMatch(words -> words >= 40 && words <= 49).anyMatch(words -> words >= 50 && words <= 70)
                .anyMatch(words -> words >= 71 && words <= 100).anyMatch(words -> words > 100);
        assertThat(metricsOf(ScoringConstants.TASK_TYPE_WRITE_ESSAY)).anyMatch(TextMetrics::allCaps);
        assertThat(metricsOf(ScoringConstants.TASK_TYPE_SUMMARIZE_WRITTEN_TEXT))
                .anyMatch(metrics -> metrics.sentenceCount() == 1 && metrics.wordCount() >= 5)
                .anyMatch(metrics -> metrics.sentenceCount() == 2)
                .anyMatch(metrics -> metrics.wordCount() < 5)
                .anyMatch(metrics -> metrics.wordCount() > 75)
                .anyMatch(TextMetrics::allCaps);
    }

    @Test
    void writingContentBandsSpanLowToHighForEveryTaskType() {
        for (String taskType : SyntheticDataset.WRITING_TASK_TYPES) {
            int max = CATALOG.find(taskType).orElseThrow().trait(RubricTraits.CONTENT).orElseThrow().scale()
                    .maxFor(null);
            List<Integer> contents = writing.samples().stream().filter(sample -> sample.taskType().equals(taskType))
                    .map(sample -> sample.intendedBands().get(RubricTraits.CONTENT)).toList();
            assertThat(contents).as(taskType).contains(max).anyMatch(content -> content <= 1);
        }
    }

    @Test
    void speakingHasFiveTranscriptsPerContentScoredTaskTypeOnTheCatalogScale() {
        assertThat(countByTaskType(speaking.samples(), SpeakingSample::taskType)).containsOnly(
                Map.entry(ScoringConstants.TASK_TYPE_DESCRIBE_IMAGE, 5L),
                Map.entry(ScoringConstants.TASK_TYPE_RE_TELL_LECTURE, 5L),
                Map.entry(ScoringConstants.TASK_TYPE_RESPOND_TO_A_SITUATION, 5L),
                Map.entry(ScoringConstants.TASK_TYPE_SUMMARIZE_GROUP_DISCUSSION, 5L));
        assertUnique(speaking.samples().stream().map(SpeakingSample::id).toList());
        assertThat(speaking.samples()).allSatisfy(sample -> {
            TaskRubric task = CATALOG.find(sample.taskType()).orElseThrow();
            assertThat(sample.transcript()).isNotBlank();
            assertThat(sample.generationNotes()).isNotBlank();
            assertThat(speaking.sources().get(sample.sourceId()).taskType()).isEqualTo(sample.taskType());
            assertThat(sample.intendedBands().keySet()).as(sample.id()).containsExactlyInAnyOrderElementsOf(
                    RubricPromptBuilder.llmTraits(task).stream().map(TraitDefinition::key).toList());
            assertOnScale(sample.id(), task, sample.intendedBands());
        });
    }

    @Test
    void ttsManifestPointsAtSpeakingSamplesWithThreeFluencyVariantsEach() {
        Set<String> ids = speaking.samples().stream().map(SpeakingSample::id).collect(Collectors.toSet());
        List<TtsEntry> entries = SyntheticDataset.ttsManifest().entries();
        assertUnique(entries.stream().map(TtsEntry::fileName).toList());
        assertThat(entries).isNotEmpty().allSatisfy(entry -> {
            assertThat(entry.fileName()).endsWith(".wav");
            assertThat(ids).contains(entry.basedOnSampleId());
            assertThat(entry.text()).isNotBlank();
        });
        Map<String, Set<String>> variants = entries.stream().collect(Collectors.groupingBy(TtsEntry::basedOnSampleId,
                Collectors.mapping(TtsEntry::variant, Collectors.toSet())));
        assertThat(variants.values()).allSatisfy(set -> assertThat(set)
                .containsExactlyInAnyOrder("FLUENT", "MILD_HESITATION", "HEAVY_HESITATION"));
    }

    /** Synthetic audio is generated on demand into SCORING_AI_SPIKE_WAV_DIR, never committed. */
    @Test
    void noAudioBinaryLivesNextToTheDataset() throws IOException, URISyntaxException {
        Path folder = Path.of(SyntheticDataset.class.getResource(SyntheticDataset.FOLDER).toURI());
        try (Stream<Path> files = Files.list(folder)) {
            assertThat(files.map(file -> file.getFileName().toString().toLowerCase(java.util.Locale.ROOT)))
                    .noneMatch(name -> name.endsWith(".wav") || name.endsWith(".mp3") || name.endsWith(".m4a"));
        }
    }

    private static void assertOnScale(String id, TaskRubric task, Map<String, Integer> bands) {
        bands.forEach((trait, band) -> assertThat(band).as("%s %s", id, trait)
                .isBetween(0, task.trait(trait).orElseThrow().scale().maxFor(null)));
    }

    private List<Integer> wordCounts(String taskType) {
        return metricsOf(taskType).stream().map(TextMetrics::wordCount).toList();
    }

    private List<TextMetrics> metricsOf(String taskType) {
        return writing.samples().stream().filter(sample -> sample.taskType().equals(taskType))
                .map(sample -> FormCheck.measure(sample.answer())).toList();
    }

    private static <T> Map<String, Long> countByTaskType(List<T> samples, Function<T, String> taskType) {
        return samples.stream().collect(Collectors.groupingBy(taskType, Collectors.counting()));
    }

    private static void assertUnique(List<String> values) {
        assertThat(new HashSet<>(values)).hasSize(values.size());
    }
}
