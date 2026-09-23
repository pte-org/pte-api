package com.pte.reporting.internal.service;

import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;

@Component
public class ReportSnapshotCodec {

    private static final int SCHEMA_VERSION = 1;

    private final ObjectMapper objectMapper;

    public ReportSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(UUID publicationPublicId, UUID publishedByPublicId, java.time.Instant publishedAt,
            int cohortSize, UUID examSnapshotPublicId, UUID scoreTemplatePublicId, Integer scoreTemplateVersion,
            AttemptScoreSummary summary,
            List<ReportScoringAnswerView> inputs) {
        List<ReportSnapshotScoreInput> savedInputs = inputs.stream().map(input -> new ReportSnapshotScoreInput(
                input.answerPublicId(), input.scoreTemplatePublicId(), input.taskType(), input.section(),
                input.scoringMethod(), input.aiRawScore(), input.aiProviderCategory(), input.aiProvider(),
                input.aiModel(), input.aiProviderVersion(), input.examinerScore(), input.selectedScoreSource(),
                input.selectedScore())).toList();
        try {
            return objectMapper.writeValueAsString(new ReportSnapshot(SCHEMA_VERSION, publicationPublicId,
                    publishedByPublicId, publishedAt, cohortSize, examSnapshotPublicId, scoreTemplatePublicId,
                    scoreTemplateVersion, summary, savedInputs));
        } catch (JacksonException ex) {
            throw new IllegalStateException(ReportingConstants.SNAPSHOT_SERIALIZATION_FAILED, ex);
        }
    }

    public ReportSnapshot decode(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException(ReportingConstants.SNAPSHOT_UNAVAILABLE);
        }
        try {
            ReportSnapshot snapshot = objectMapper.readValue(json, ReportSnapshot.class);
            if (snapshot == null || snapshot.schemaVersion() != SCHEMA_VERSION || snapshot.scoreSummary() == null) {
                throw new IllegalStateException(ReportingConstants.SNAPSHOT_VERSION_UNSUPPORTED);
            }
            return snapshot;
        } catch (JacksonException ex) {
            throw new IllegalStateException(ReportingConstants.SNAPSHOT_UNAVAILABLE, ex);
        }
    }
}
