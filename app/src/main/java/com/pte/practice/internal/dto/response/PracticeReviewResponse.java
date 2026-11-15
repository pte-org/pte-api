package com.pte.practice.internal.dto.response;

import com.pte.practice.internal.dto.response.mocktest.PracticeEstimatedScoresResponse;

import java.util.List;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;

/** {@code kind} (TASK, SKILL or MOCK_TEST) tells the student page how AI scoring is asked for: per answer or per test. */
public record PracticeReviewResponse(UUID sessionPublicId, List<Item> items,
        PracticeEstimatedScoresResponse estimatedScores, String kind) {

    public PracticeReviewResponse(UUID sessionPublicId, List<Item> items) {
        this(sessionPublicId, items, null, null);
    }

    public PracticeReviewResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }

    public record Item(UUID publicId, int orderIndex, String taskCode, String displayName, String section,
            String status, String promptText, String savedPayload, String rendererKey, Integer objectiveScore,
            Instant objectiveEvaluatedAt, Integer aiScore, String aiScoreStatus, Map<String, Integer> aiSubScores,
            String aiProvider, String aiModel, Instant aiScoredAt, List<PracticeTaskContentResponse.Option> options,
            PracticeItemResultResponse result,
            /** CEFR level of the source question; null when it is unclassified. */
            String cefrLevel) {

        public Item {
            aiSubScores = aiSubScores == null ? Map.of() : Map.copyOf(aiSubScores);
            options = options == null ? List.of() : List.copyOf(options);
        }
    }
}
