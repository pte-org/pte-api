package com.pte.reporting.internal.service;

import com.pte.reporting.domain.enums.Skill;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportSnapshotCodecTest {

    private final ReportSnapshotCodec codec = new ReportSnapshotCodec(JsonMapper.builder().findAndAddModules().build());

    @Test
    void roundTripPreservesPinnedAggregateAndBothSourceValues() {
        UUID answerId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        UUID publicationId = UUID.randomUUID();
        UUID publisherId = UUID.randomUUID();
        UUID examSnapshotId = UUID.randomUUID();
        UUID scoreTemplateId = UUID.randomUUID();
        int scoreTemplateVersion = 7;
        Instant publishedAt = Instant.parse("2026-09-24T10:15:30Z");
        AttemptScoreSummary summary = new AttemptScoreSummary(null, Map.of(Skill.WRITING, SkillScore.of(81)));
        ReportScoringAnswerView input = new ReportScoringAnswerView(answerId, UUID.randomUUID(), templateId,
                "WRITE_ESSAY", "WRITING", "AI_TEXT", 75, "REAL", "provider", "model", "v2", 81,
                "EXAMINER", 81, true, null, 3);

        String json = codec.encode(publicationId, publisherId, publishedAt, 4, examSnapshotId, scoreTemplateId,
                scoreTemplateVersion, summary, List.of(input));
        ReportSnapshot snapshot = codec.decode(json);

        assertThat(snapshot.publicationPublicId()).isEqualTo(publicationId);
        assertThat(snapshot.publishedByPublicId()).isEqualTo(publisherId);
        assertThat(snapshot.publishedAt()).isEqualTo(publishedAt);
        assertThat(snapshot.cohortSize()).isEqualTo(4);
        assertThat(snapshot.examSnapshotPublicId()).isEqualTo(examSnapshotId);
        assertThat(snapshot.scoreTemplatePublicId()).isEqualTo(scoreTemplateId);
        assertThat(snapshot.scoreTemplateVersion()).isEqualTo(scoreTemplateVersion);
        assertThat(snapshot.scoreSummary()).isEqualTo(summary);
        assertThat(snapshot.scoreInputs()).containsExactly(new ReportSnapshotScoreInput(answerId, templateId,
                "WRITE_ESSAY", "WRITING", "AI_TEXT", 75, "REAL", "provider", "model", "v2", 81,
                "EXAMINER", 81));
    }
}
