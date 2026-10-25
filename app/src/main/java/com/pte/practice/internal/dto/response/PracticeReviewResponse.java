package com.pte.practice.internal.dto.response;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;

public record PracticeReviewResponse(UUID sessionPublicId, List<Item> items,
        PracticeEstimatedScoresResponse estimatedScores) {

    public PracticeReviewResponse(UUID sessionPublicId, List<Item> items) {
        this(sessionPublicId, items, null);
    }

    public PracticeReviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(UUID publicId, int orderIndex, String taskCode, String displayName, String section,
            String status, String promptText, String savedPayload, String rendererKey, Integer objectiveScore,
            Instant objectiveEvaluatedAt, Integer aiScore, String aiScoreStatus, Map<String, Integer> aiSubScores,
            String aiProvider, String aiModel, Instant aiScoredAt, List<PracticeTaskContentResponse.Option> options) {

        public Item {
            aiSubScores = aiSubScores == null ? Map.of() : Map.copyOf(aiSubScores);
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
