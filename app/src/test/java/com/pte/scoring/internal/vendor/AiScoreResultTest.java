package com.pte.scoring.internal.vendor;

import com.pte.scoring.domain.enums.AiProviderCategory;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiScoreResultTest {

    @Test
    void result_validatesAndDefensivelyCopiesScores() {
        Map<String, Integer> scores = new HashMap<>();
        scores.put("GRAMMAR", 80);

        AiScoreResult result = new AiScoreResult(82, scores, null,
                AiProviderCategory.REAL, "TEST_PROVIDER", "test-model", null);
        scores.put("GRAMMAR", 0);

        assertThat(result.rawScore()).isEqualTo(82);
        assertThat(result.subScores()).containsEntry("GRAMMAR", 80);
        assertThat(result.feedback()).isEmpty();
        assertThat(result.providerCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(result.provider()).isEqualTo("TEST_PROVIDER");
        assertThat(result.model()).isEqualTo("test-model");
    }

    @Test
    void result_carriesTranscriptDetailAndFallbackFlag() {
        AiScoreResult full = new AiScoreResult(70, Map.of("content", 60), "feedback",
                AiProviderCategory.REAL, "AZURE_GEMINI", "model", null, "the transcript", "{\"words\":3}", true);
        AiScoreResult legacy = new AiScoreResult(70, Map.of(), "feedback",
                AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "model", null);

        assertThat(full.transcript()).isEqualTo("the transcript");
        assertThat(full.detailJson()).isEqualTo("{\"words\":3}");
        assertThat(full.fallback()).isTrue();
        assertThat(legacy.transcript()).isNull();
        assertThat(legacy.detailJson()).isNull();
        assertThat(legacy.fallback()).isFalse();
    }

    @Test
    void result_toStringLeavesOutCandidateContent() {
        AiScoreResult result = new AiScoreResult(70, Map.of(), "secret feedback",
                AiProviderCategory.REAL, "AZURE_GEMINI", "model", null, "secret transcript", "{\"secret\":1}", false);

        assertThat(result.toString()).contains("70", "AZURE_GEMINI", "model")
                .doesNotContain("secret");
    }

    @Test
    void result_rejectsOutOfRangeRawAndSubScores() {
        assertThatThrownBy(() -> new AiScoreResult(101, Map.of(), "feedback",
                AiProviderCategory.REAL, "TEST_PROVIDER", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiScoreResult(80, Map.of("GRAMMAR", -1), "feedback",
                AiProviderCategory.REAL, "TEST_PROVIDER", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        Map<String, Integer> nullSubScore = new HashMap<>();
        nullSubScore.put("GRAMMAR", null);
        assertThatThrownBy(() -> new AiScoreResult(80, nullSubScore, "feedback",
                AiProviderCategory.REAL, "TEST_PROVIDER", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiScoreResult(80, Map.of(), "feedback",
                null, "TEST_PROVIDER", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiScoreResult(80, Map.of(), "feedback",
                AiProviderCategory.REAL, "P".repeat(65), null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
