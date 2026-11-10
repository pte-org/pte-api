package com.pte.scoring.internal.vendor.gemini.scorer;

import com.pte.scoring.internal.constant.ScoringConstants;
import com.pte.scoring.internal.rubric.catalog.RubricCatalogImpl;
import com.pte.scoring.internal.vendor.AiScoreResult;
import com.pte.scoring.internal.vendor.AiScoringRequest;
import com.pte.scoring.internal.vendor.RecordingHttpServer;
import com.pte.scoring.internal.vendor.gemini.GeminiRestClient;
import com.pte.scoring.rubric.EffectiveRubric;
import com.pte.scoring.rubric.RubricCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What actually goes over the wire for a rubric-scored essay: temperature 0, the chosen schema variant (b) (INTEGER
 * with minimum/maximum per AI_LLM trait, no Form field), the catalog's level descriptions in the system instruction,
 * and on a schema violation a byte-identical second request.
 */
class RubricWritingWireTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final RubricCatalog CATALOG = new RubricCatalogImpl();
    private static final String WE = ScoringConstants.TASK_TYPE_WRITE_ESSAY;
    private static final String VALID = "{\"CONTENT\": 5, \"DEVELOPMENT_STRUCTURE_COHERENCE\": 5, \"GRAMMAR\": 2, "
            + "\"GENERAL_LINGUISTIC_RANGE\": 5, \"VOCABULARY_RANGE\": 2, \"SPELLING\": 2, \"feedback\": \"ok\", "
            + "\"errors\": []}";

    private RecordingHttpServer server;
    private WriteEssayScorer scorer;

    @BeforeEach
    void start() throws IOException {
        server = RecordingHttpServer.start();
        GeminiRestClient client = new GeminiRestClient(
                HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build(), JSON, server.uri(""), "key",
                "gemini-test", Duration.ofSeconds(5));
        scorer = new WriteEssayScorer(client, JSON);
    }

    @AfterEach
    void stop() {
        server.close();
    }

    @Test
    void theRequestHasTemperatureZeroAndTheIntegerRangeSchemaWithoutForm() {
        server.enqueue(200, candidate(VALID));

        AiScoreResult result = scorer.score(essayRequest());

        JsonNode config = body(0).path("generationConfig");
        assertThat(config.path("temperature").isNumber()).isTrue();
        assertThat(config.path("temperature").asDouble()).isZero();
        assertThat(config.path("responseMimeType").asText()).isEqualTo("application/json");
        JsonNode properties = config.path("responseSchema").path("properties");
        assertThat(properties.path("CONTENT").path("type").asText()).isEqualTo("INTEGER");
        assertThat(properties.path("CONTENT").path("minimum").asInt()).isZero();
        assertThat(properties.path("CONTENT").path("maximum").asInt()).isEqualTo(6);
        assertThat(properties.path("SPELLING").path("maximum").asInt()).isEqualTo(2);
        assertThat(properties.path("CONTENT").has("enum")).isFalse();
        assertThat(properties.has("FORM")).isFalse();
        assertThat(properties.has("form")).isFalse();
        String system = body(0).path("systemInstruction").path("parts").path(0).path("text").asText();
        assertThat(system).contains("6: The essay fully addresses the prompt in depth")
                .contains("0: The essay does not properly deal with the prompt.").contains(GeminiScorers.GUARD);
        assertThat(result.rawScore()).isEqualTo(88); // 5+2+5+2+5+2+2 = 23 of 26 points
    }

    @Test
    void aSchemaViolationIsAskedAgainWithAByteIdenticalRequest() {
        server.enqueue(200, candidate(VALID.replace("\"CONTENT\": 5", "\"CONTENT\": 7")));
        server.enqueue(200, candidate(VALID));

        AiScoreResult result = scorer.score(essayRequest());

        assertThat(server.requests()).hasSize(2);
        assertThat(server.requests().get(1).bodyText()).isEqualTo(server.requests().get(0).bodyText());
        assertThat(JSON.readTree(result.detailJson()).path("schemaAttempts").path("count").asInt()).isEqualTo(2);
    }

    private JsonNode body(int index) {
        return JSON.readTree(server.requests().get(index).bodyText());
    }

    private static AiScoringRequest essayRequest() {
        EffectiveRubric rubric = new EffectiveRubric(CATALOG.find(WE).orElseThrow(), CATALOG.defaultParameters(WE));
        return new AiScoringRequest(WE, essay(), "Should cities ban cars?", null, null, null, null, null,
                UUID.randomUUID(), rubric, UUID.randomUUID());
    }

    /** 250 words in ten-word sentences: Form 2. */
    private static String essay() {
        return ("word ".repeat(10).trim() + ". ").repeat(25).trim();
    }

    private static String candidate(String text) {
        return JSON.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of("role", "model", "parts", List.of(Map.of("text", text))),
                        "finishReason", "STOP")),
                "modelVersion", "gemini-test"));
    }
}
