package com.pte.scoring.domain;

import com.pte.scoring.domain.enums.AiProviderCategory;
import com.pte.scoring.domain.enums.ScoreSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScoringAnswerWorkflowTest {

    @Test
    void legacyOrStubRawScoreCannotBeSelectedAsAiSource() {
        ScoringAnswer legacy = new ScoringAnswer();
        assertThatThrownBy(() -> legacy.markScored(101)).isInstanceOf(IllegalArgumentException.class);
        legacy.markScored(70);
        assertThatThrownBy(() -> legacy.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);

        ScoringAnswer stub = new ScoringAnswer();
        stub.markAiScored(65, AiProviderCategory.STUB, "STUB", null, null);
        assertThatThrownBy(() -> stub.selectScoreSource(ScoreSource.AI, UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void realAiProvenanceIsStoredWithTheRawScoreAndCanBeSelected() {
        ScoringAnswer answer = new ScoringAnswer();
        answer.markAiScored(82, AiProviderCategory.REAL, "OPENAI_COMPATIBLE", "model-v2", null);
        UUID actor = UUID.randomUUID();
        Instant selectedAt = Instant.parse("2026-09-23T10:00:00Z");

        answer.selectScoreSource(ScoreSource.AI, actor, selectedAt);

        assertThat(answer.getRawScore()).isEqualTo(82);
        assertThat(answer.getAiProviderCategory()).isEqualTo(AiProviderCategory.REAL);
        assertThat(answer.getAiProvider()).isEqualTo("OPENAI_COMPATIBLE");
        assertThat(answer.getAiModel()).isEqualTo("model-v2");
        assertThat(answer.getSelectedScoreSource()).isEqualTo(ScoreSource.AI);
        assertThat(answer.getSelectedScoreSourceByPublicId()).isEqualTo(actor);
        assertThat(answer.getSelectedScoreSourceAt()).isEqualTo(selectedAt);
        assertThat(answer.getTeacherScore()).isNull();
    }
}
