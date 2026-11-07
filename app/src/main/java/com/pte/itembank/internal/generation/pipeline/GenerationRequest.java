package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;

import java.util.UUID;

/**
 * What the generator is asked to make for one slot of a job. {@code feedback} is the reviewer's suggestion when this
 * is the rewrite of a draft the reviewer rejected; it is null on the first attempt.
 */
public record GenerationRequest(UUID jobPublicId, int itemIndex, String taskTypeKey, CefrLevel cefrLevel,
                                QuestionPool pool, String topicHint, String avoidTopics, String feedback) {

    public GenerationRequest(UUID jobPublicId, int itemIndex, String taskTypeKey, CefrLevel cefrLevel,
                             QuestionPool pool, String topicHint, String avoidTopics) {
        this(jobPublicId, itemIndex, taskTypeKey, cefrLevel, pool, topicHint, avoidTopics, null);
    }

    public GenerationRequest(UUID jobPublicId, int itemIndex, String taskTypeKey, CefrLevel cefrLevel,
                             QuestionPool pool, String topicHint) {
        this(jobPublicId, itemIndex, taskTypeKey, cefrLevel, pool, topicHint, "", null);
    }

    /** The same request, now carrying what the reviewer asked to change. */
    public GenerationRequest withFeedback(String reviewerFeedback) {
        return new GenerationRequest(jobPublicId, itemIndex, taskTypeKey, cefrLevel, pool, topicHint, avoidTopics,
                reviewerFeedback);
    }
}
