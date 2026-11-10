package com.pte.scoring.internal.rubric.service;

import com.pte.assessment.ExamDefinitionService;
import com.pte.scoring.domain.ScoringRubricVersion.RubricVersionStatus;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest;
import com.pte.scoring.internal.rubric.dto.RubricSimulationRequest.SimulationTextMetrics;
import com.pte.scoring.internal.rubric.repository.ScoringRubricVersionRepository;
import com.pte.scoring.internal.vendor.*;
import com.pte.scoring.internal.vendor.audio.AudioChunk;
import com.pte.scoring.internal.vendor.azure.*;
import com.pte.scoring.internal.vendor.gemini.*;
import com.pte.scoring.internal.vendor.gemini.scorer.*;
import com.pte.scoring.internal.vendor.speech.SpeechAudioLoader;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.TraitSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.*;

import static com.pte.scoring.internal.rubric.service.RubricVersionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** Actual production scorers with deterministic Gemini/Azure responses, compared with the sandbox API service. */
class ScorerSimulatorPipelineParityTest {
    private final ScoringRubricVersionRepository versions = mock(ScoringRubricVersionRepository.class);
    private final RubricResolver resolver = new RubricResolver(versions, CATALOG, CODEC, mock(ExamDefinitionService.class));
    private final RubricSimulationService simulator = new RubricSimulationService(versions, resolver);
    private final GeminiRestClient gemini = mock(GeminiRestClient.class);
    private final SpeechAudioLoader audio = mock(SpeechAudioLoader.class);
    private final AzureSpeechAssessor azure = mock(AzureSpeechAssessor.class);

    @ParameterizedTest
    @ValueSource(strings = {"DESCRIBE_IMAGE", "RE_TELL_LECTURE", "RESPOND_TO_A_SITUATION", "SUMMARIZE_GROUP_DISCUSSION",
            "SUMMARIZE_WRITTEN_TEXT", "WRITE_ESSAY", "SUMMARIZE_SPOKEN_TEXT"})
    void actualScorersMatchForBothContentAndFormGates(String task) {
        for (boolean gates : List.of(true, false)) {
            var version = version(RubricVersionStatus.DRAFT, editTask(task, node -> {
                child(node, "gateEnabled").put("CONTENT", gates);
                if (child(node, "gateEnabled").has("FORM")) child(node, "gateEnabled").put("FORM", gates);
                child(node, "traitWeights").put("CONTENT", 2);
            }));
            when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
            EffectiveRubric rubric = resolver.effectiveFor(version, task);
            for (int content : List.of(0, 1)) {
                for (int words : List.of(30, 60, 150, 250)) {
                    var model = JSON.createObjectNode();
                    rubric.task().traits().stream().filter(trait -> trait.source() == TraitSource.AI_LLM)
                            .forEach(trait -> model.put(trait.key(), trait.key().equals("CONTENT") ? content : 1));
                    model.put("feedback", "Synthetic deterministic response.");
                    when(gemini.generate(any())).thenReturn(new GeminiResponse(model, "test-model"));
                    String answer = "Synthetic " + "word ".repeat(words - 2) + "response.";
                    UUID tenant = UUID.randomUUID();
                    byte[] bytes = {1};
                    var chunks = List.of(new AudioChunk(bytes, 0, 5000));
                    lenient().when(audio.load(answer, tenant)).thenReturn(bytes);
                    lenient().when(azure.prepare(bytes)).thenReturn(chunks);
                    lenient().when(azure.assess(chunks, "")).thenReturn(new SpeechAssessment(90.0, 90.0, 90.0,
                            List.of(), List.of(new SpeechSegmentResult(true, answer, answer, 0, 50000000, null, List.of())), 5000));
                    var actual = scorer(task).score(new AiScoringRequest(task, answer, "Synthetic source.",
                            "Synthetic reference.", null, null, null, null, tenant, rubric, version.getPublicId()));
                    var scores = new LinkedHashMap<String, BigDecimal>();
                    actual.subScores().forEach((key, value) -> { if (!key.equals("FORM")) scores.put(key, BigDecimal.valueOf(value)); });
                    clearInvocations(gemini, azure, audio);
                    var simulated = simulator.simulate(new RubricSimulationRequest(task, version.getPublicId(), scores,
                            new SimulationTextMetrics(words, 1, false, true, false), null));
                    assertThat(simulated.rawScore()).as("%s gates=%s content=%s words=%s", task, gates, content, words)
                            .isEqualTo(actual.rawScore());
                    verifyNoInteractions(gemini, azure, audio);
                }
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 30, 80})
    void actualReadAloudScorerMatchesAtEachDynamicScale(int words) {
        var version = version(RubricVersionStatus.ACTIVE, seedJson());
        when(versions.findByPublicId(version.getPublicId())).thenReturn(Optional.of(version));
        var rubric = resolver.effectiveFor(version, "READ_ALOUD");
        String passage = "word ".repeat(words).trim();
        UUID tenant = UUID.randomUUID();
        byte[] bytes = {1};
        var chunks = List.of(new AudioChunk(bytes, 0, 5000));
        when(audio.load("media", tenant)).thenReturn(bytes);
        when(azure.prepare(bytes)).thenReturn(chunks);
        var heard = new SpeechAssessment(90.0, 90.0, 90.0, List.of(),
                List.of(new SpeechSegmentResult(true, passage, passage, 0, 50000000, null, List.of())), 5000);
        when(azure.assessScripted(chunks, passage)).thenReturn(new ScriptedAssessment(heard, heard));
        var actual = new ReadAloudScorer(audio, azure, JSON).score(new AiScoringRequest("READ_ALOUD", "media",
                passage, null, null, null, null, null, tenant, rubric, version.getPublicId()));
        Map<String, BigDecimal> traits = new LinkedHashMap<>();
        actual.subScores().forEach((key, value) -> traits.put(key, BigDecimal.valueOf(value)));
        clearInvocations(gemini, azure, audio);
        var simulated = simulator.simulate(new RubricSimulationRequest("READ_ALOUD", version.getPublicId(), traits, null, words));
        assertThat(simulated.rawScore()).isEqualTo(actual.rawScore());
        verifyNoInteractions(gemini, azure, audio);
    }

    private AiAnswerScorer scorer(String task) {
        return switch (task) {
            case "WRITE_ESSAY" -> new WriteEssayScorer(gemini, JSON);
            case "SUMMARIZE_WRITTEN_TEXT" -> new SummarizeWrittenTextScorer(gemini, JSON);
            case "SUMMARIZE_SPOKEN_TEXT" -> new SummarizeSpokenTextScorer(gemini, JSON);
            case "DESCRIBE_IMAGE" -> new DescribeImageScorer(audio, azure, gemini, JSON, mock(PromptImageLoader.class));
            case "RE_TELL_LECTURE" -> new RetellLectureScorer(audio, azure, gemini, JSON);
            case "RESPOND_TO_A_SITUATION" -> new RespondToSituationScorer(audio, azure, gemini, JSON);
            default -> new SummarizeGroupDiscussionScorer(audio, azure, gemini, JSON);
        };
    }
}
