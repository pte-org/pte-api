package com.pte.reporting.internal.service;

import com.pte.reporting.internal.constant.ReportingConstants;
import com.pte.scoring.dto.response.ItemScoreTraceView;
import com.pte.scoring.dto.response.ReportScoringAnswerView;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes schema-2 snapshots and reads schema 1 and 2. A schema-1 snapshot (published before score explanations)
 * decodes with the new fields null and {@link #traceAvailable} false; any other schema version is rejected.
 */
@Component
public class ReportSnapshotCodec {

    private final ObjectMapper objectMapper;

    public ReportSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Header fields of one published report; the scores come from {@code aggregation}. */
    public record Publication(UUID publicationPublicId, UUID publishedByPublicId, Instant publishedAt,
                              int cohortSize, UUID examSnapshotPublicId) {
    }

    public String encode(Publication publication, ReportScoreAggregation aggregation,
            List<ReportScoringAnswerView> inputs, Map<UUID, ItemScoreTraceView> itemTraces) {
        List<ReportSnapshotScoreInput> savedInputs = inputs.stream()
                .map(input -> toSnapshotInput(input, itemTraces.get(input.answerPublicId()))).toList();
        ReportSnapshot snapshot = new ReportSnapshot(ReportingConstants.SNAPSHOT_SCHEMA_VERSION_TRACED,
                publication.publicationPublicId(), publication.publishedByPublicId(), publication.publishedAt(),
                publication.cohortSize(), publication.examSnapshotPublicId(),
                aggregation.context().examDefinitionPublicId(), aggregation.context().examDefinitionVersion(),
                aggregation.summary(), savedInputs, aggregation.rubricVersionPublicId(), aggregation.trace());
        try {
            return objectMapper.writeValueAsString(snapshot);
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
            if (snapshot == null || !isSupported(snapshot.schemaVersion()) || snapshot.scoreSummary() == null) {
                throw new IllegalStateException(ReportingConstants.SNAPSHOT_VERSION_UNSUPPORTED);
            }
            return snapshot;
        } catch (JacksonException ex) {
            throw new IllegalStateException(ReportingConstants.SNAPSHOT_UNAVAILABLE, ex);
        }
    }

    /** Whether the snapshot carries the aggregation trace (schema 2); a schema-1 snapshot has scores only. */
    public static boolean traceAvailable(ReportSnapshot snapshot) {
        return snapshot.schemaVersion() >= ReportingConstants.SNAPSHOT_SCHEMA_VERSION_TRACED
                && snapshot.aggregationTrace() != null;
    }

    private static boolean isSupported(int schemaVersion) {
        return schemaVersion == ReportingConstants.SNAPSHOT_SCHEMA_VERSION_LEGACY
                || schemaVersion == ReportingConstants.SNAPSHOT_SCHEMA_VERSION_TRACED;
    }

    private static ReportSnapshotScoreInput toSnapshotInput(ReportScoringAnswerView input, ItemScoreTraceView trace) {
        return new ReportSnapshotScoreInput(input.answerPublicId(), input.examDefinitionPublicId(), input.taskType(),
                input.section(), input.scoringMethod(), input.aiRawScore(), input.aiProviderCategory(),
                input.aiProvider(), input.aiModel(), input.aiProviderVersion(), input.examinerScore(),
                input.selectedScoreSource(), input.selectedScore(), input.traitScores(),
                input.rubricVersionPublicId(), input.wordCount(), trace == null ? null : trace.kind(),
                trace == null ? null : trace.objectiveRuleCode(), trace == null ? null : trace.trace());
    }
}
