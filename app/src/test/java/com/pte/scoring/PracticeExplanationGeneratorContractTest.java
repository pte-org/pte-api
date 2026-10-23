package com.pte.scoring;

import org.junit.jupiter.api.Test;
import com.pte.scoring.internal.vendor.gemini.PracticeGeminiExplanationGenerator;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Set;

import com.pte.scoring.internal.vendor.gemini.GeminiRequest;
import com.pte.scoring.internal.vendor.gemini.GeminiResponse;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeExplanationGeneratorContractTest {

    @Test
    void legacyRequestKeepsVersionCompatibilityWhileNewContextCarriesHash() {
        PracticeExplanationGenerator.ExplanationRequest request =
                new PracticeExplanationGenerator.ExplanationRequest("WRITE_ESSAY", "prompt", "answer",
                        null, null, null, "vi", "legacy-v1");

        assertThat(request.rubricVersion()).isEqualTo("legacy-v1");
        assertThat(request.rubricContext()).isNull();

        PracticeExplanationGenerator.RubricContext context = new PracticeExplanationGenerator.RubricContext(
                UUID.randomUUID(), 2, "hash-2", "{}", "runtime-hash");
        PracticeExplanationGenerator.ExplanationRequest pinned =
                new PracticeExplanationGenerator.ExplanationRequest("WRITE_ESSAY", "prompt", "answer",
                        null, null, null, "vi", "2", context);
        assertThat(pinned.rubricContext().canonicalHash()).isEqualTo("hash-2");
    }

    @Test
    void providerRejectsExplanationRequestsWithoutPinnedRubric() {
        PracticeExplanationGenerator generator = new PracticeGeminiExplanationGenerator(null,
                JsonMapper.builder().build(), new PracticeAiProperties());
        PracticeExplanationGenerator.ExplanationRequest legacy =
                new PracticeExplanationGenerator.ExplanationRequest("WRITE_ESSAY", "prompt", "answer",
                        null, null, null, "vi", "legacy-v1");

        assertThatThrownBy(() -> generator.generate(legacy))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PRACTICE_AI_RUBRIC_REQUIRED");
    }

    @Test
    void providerRejectsEvidenceThatIsNotBoundToTheSubmittedAttempt() {
        JsonMapper mapper = JsonMapper.builder().build();
        String response = """
                {"summary":"Good","strengths":["Clear"],"improvements":["Add support"],
                 "limitations":[],"rubricVersion":"1","language":"vi","criterionFeedback":[
                 {"criterionId":"TASK_FULFILMENT","level":"DEVELOPING","evidenceCoverage":"LIMITED",
                  "feedback":"Needs more support","evidence":["task_prompt.source"]}]}
                """;
        GeminiRestClient client = new GeminiRestClient(HttpClient.newHttpClient(), mapper,
                URI.create("http://localhost"), "test-key", "test-model", Duration.ofSeconds(1)) {
            @Override
            public GeminiResponse generate(GeminiRequest request) {
                try {
                    return new GeminiResponse(mapper.readTree(response), "test-model");
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }
        };
        PracticeExplanationGenerator generator = new PracticeGeminiExplanationGenerator(client, mapper,
                new PracticeAiProperties());
        PracticeExplanationGenerator.ExplanationRequest request = new PracticeExplanationGenerator.ExplanationRequest(
                "WRITE_ESSAY", "prompt", "answer", null, null, null, "vi", "1",
                new PracticeExplanationGenerator.RubricContext(UUID.randomUUID(), 1, "hash", """
                        {"criteria":[{"criterionId":"TASK_FULFILMENT","order":1,"required":true,
                        "allowedLevels":["DEVELOPING"],"evidencePolicy":{"requiredTypes":["PROMPT"],
                        "allowNotAssessable":false,"minimumCoverage":"LIMITED"}}]}
                        """, "runtime"), null, Set.of("PROMPT", "RESPONSE"));

        assertThatThrownBy(() -> generator.generate(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("PRACTICE_AI_RESPONSE_INVALID");
    }
}
