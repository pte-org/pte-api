package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.GradingMarkingMode;
import com.pte.scoring.domain.enums.ScoringAnswerStatus;
import com.pte.scoring.domain.enums.ScoringMethod;
import com.pte.scoring.support.EvaluationFixtures;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEvaluationWorkflowTest {

    @Test
    void legacyOrStubRawScoreIsNeverAPublishableAiScore() {
        AiEvaluation legacy = evaluation();
        assertThatThrownBy(() -> legacy.markScored(101)).isInstanceOf(IllegalArgumentException.class);
        legacy.markScored(70);
        assertThat(legacy.hasPublishableAiScore()).isFalse();

        AiEvaluation stub = evaluation();
        stub.markAiScored(65, AiProviderCategory.STUB, "STUB", null, null);
        assertThat(stub.hasPublishableAiScore()).isFalse();
    }

    @Test
    void realAiProvenanceIsStoredWithTheRawScore() {
        AiEvaluation answer = evaluation();
        answer.markAiScored(82, AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "model-v2", null);

        assertThat(answer.getRawScore()).isEqualTo(82);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(answer.getAiProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(answer.getAiModel()).isEqualTo("model-v2");
        assertThat(answer.hasPublishableAiScore()).isTrue();
        assertThat(answer.getTeacherScore()).isNull();
    }

    @Test
    void eachAiDispatchIssuesANewTokenAndOnlyTheLatestAwaitsAResult() {
        AiEvaluation answer = evaluation();
        Instant firstDispatch = Instant.parse("2026-10-11T10:00:00Z");
        Instant secondDispatch = Instant.parse("2026-10-11T10:20:00Z");

        long first = answer.markAiDispatched(firstDispatch);

        assertThat(answer.getStatus()).isEqualTo(ScoringAnswerStatus.AI_SCORING);
        assertThat(answer.getAiDispatchedAt()).isEqualTo(firstDispatch);
        assertThat(answer.isAwaitingAiResult(first)).isTrue();

        long second = answer.markAiDispatched(secondDispatch);

        assertThat(second).isGreaterThan(first);
        assertThat(answer.getAiDispatchedAt()).isEqualTo(secondDispatch);
        assertThat(answer.isAwaitingAiResult(first)).isFalse();
        assertThat(answer.isAwaitingAiResult(second)).isTrue();

        answer.markAiScored(70, AiProviderCategory.REAL, "AZURE_GEMINI", "model", null);
        assertThat(answer.isAwaitingAiResult(second)).isFalse();
    }

    @Test
    void aFailedDispatchKeepsTheLastResultButReportsTheFailure() {
        AiEvaluation answer = evaluation();
        answer.markAiScored(70, AiProviderCategory.REAL, "AZURE_GEMINI", "model", null);
        answer.recordDetail("{\"CONTENT\":70}", "ok", null, "{}", false);

        answer.recordFailure(com.pte.scoring.domain.enums.AiFailureReason.PROVIDER_TIMEOUT);

        assertThat(answer.getCriteriaScoresJson()).isEqualTo("{\"CONTENT\":70}");
        assertThat(answer.hasAiOutcome()).isTrue();
        assertThat(answer.getFailureReason())
                .isEqualTo(com.pte.scoring.domain.enums.AiFailureReason.PROVIDER_TIMEOUT);
    }

    private AiEvaluation evaluation() {
        return EvaluationFixtures.ai("READ_ALOUD", ScoringMethod.AI_SPEECH);
    }
}
