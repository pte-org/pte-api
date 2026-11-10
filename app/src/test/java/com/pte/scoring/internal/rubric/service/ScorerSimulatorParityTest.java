package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest.SimulationTextMetrics;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.rubric.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScorerSimulatorParityTest {
    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final RubricResolver resolver = new RubricResolver(versions, CATALOG, CODEC, mock(ExamDefinitionService.class));
    private final RubricSimulationService simulator = new RubricSimulationService(versions, resolver);

    @ParameterizedTest
    @ValueSource(strings = {"DESCRIBE_IMAGE", "RE_TELL_LECTURE", "RESPOND_TO_A_SITUATION",
            "SUMMARIZE_GROUP_DISCUSSION", "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT"})
    void allSevenGatedTasksMatchIncludingDisabledGatesAndChangedWeights(String task) {
        for (boolean enabled : List.of(true, false)) {
            String parameters = editTask(task, node -> {
                child(node, "gateEnabled").put("CONTENT", enabled);
                child(node, "traitWeights").put("CONTENT", 2);
            });
            for (int content : List.of(0, 1)) {
                assertParity(task, parameters, content, new SimulationTextMetrics(60, 1, false, true, false), null);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT"})
    void formBandsAndMechanicalRulesMatch(String task) {
        for (int words : List.of(0, 30, 45, 60, 150, 250, 400)) {
            for (var metrics : List.of(new SimulationTextMetrics(words, 1, false, true, false),
                    new SimulationTextMetrics(words, 2, false, true, false),
                    new SimulationTextMetrics(words, 1, true, true, false),
                    new SimulationTextMetrics(words, 1, false, false, false),
                    new SimulationTextMetrics(words, 1, false, true, true))) {
                assertParity(task, seedJson(), 1, metrics, null);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 80})
    void readAloudUsesThePassageLength(int words) {
        assertParity("READ_ALOUD", seedJson(), 1, null, words);
    }

    @Test
    void fixtureLatencyP95Below300msWithoutADatabase() {
        long[] times = new long[120];
        for (int i = 0; i < times.length; i++) {
            long start = System.nanoTime();
            assertParity("DESCRIBE_IMAGE", seedJson(), 1, null, null);
            times[i] = System.nanoTime() - start;
        }
        Arrays.sort(times);
        assertThat(times[113] / 1_000_000.0).isLessThan(300);
        System.out.println("Simulator fixture p95 ms=" + times[113] / 1_000_000.0);
    }

    private void assertParity(String task, String parameters, int content, SimulationTextMetrics metrics, Integer words) {
        var version = version(RubricVersionStatus.RETIRED, parameters);
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        EffectiveRubric rubric = resolver.effectiveFor(version, task);
        Map<String, BigDecimal> input = new LinkedHashMap<>();
        Map<String, Integer> exact = new LinkedHashMap<>();
        for (var trait : rubric.task().traits()) {
            int achieved = trait.key().equals("CONTENT") ? content : rubric.maxOf(trait, words);
            exact.put(trait.key(), achieved);
            if (!trait.key().equals("FORM")) {
                input.put(trait.key(), BigDecimal.valueOf(achieved));
            }
        }
        if (metrics != null && rubric.effectiveFormRule() != null) {
            exact.put("FORM", rubric.effectiveFormRule().bandFor(new TextMetrics(metrics.wordCount(),
                    metrics.sentenceCount(), metrics.allCaps(), metrics.punctuationPresent(), metrics.bulletOnly())));
        }
        var result = simulator.simulate(new RubricSimulationRequest(task, version.getPublicId(), input, metrics, words));
        assertThat(result.scoreTrace()).isEqualTo(RubricScorer.score(rubric, new TraitScoreSet(exact, words)));
        assertThat(result.rawScore()).isEqualTo(result.scoreTrace().itemScore());
    }
}
