package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.internal.generation.constant.QuestionGenerationConstants;
import com.pte.itembank.internal.generation.domain.ImageKind;
import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;

import java.util.UUID;

/**
 * What the generator is asked to make for one slot of a job. {@code feedback} is the reviewer's suggestion when this
 * is the rewrite of a draft the reviewer rejected; it is null on the first attempt.
 */
public record GenerationRequest(UUID jobPublicId, int itemIndex, String taskTypeKey, CefrLevel cefrLevel,
                                QuestionPool pool, String topicHint, String avoidTopics, String feedback,
                                ImageKind imageKind) {

    public GenerationRequest(UUID jobPublicId, int itemIndex, String taskTypeKey, CefrLevel cefrLevel,
                             QuestionPool pool, String topicHint, String avoidTopics, String feedback) {
        this(jobPublicId, itemIndex, taskTypeKey, cefrLevel, pool, topicHint, avoidTopics, feedback, ImageKind.CHART);
    }

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
                reviewerFeedback, imageKind);
    }

    private static final String DESCRIBE_IMAGE = "DESCRIBE_IMAGE";

    /** A Describe Image question whose image the image model paints. */
    public boolean isPicture() {
        return DESCRIBE_IMAGE.equals(taskTypeKey) && imageKind == ImageKind.PICTURE;
    }

    /** The writer prompt, parser and mapping to use: the picture variant has its own. */
    public String promptKey() {
        return isPicture() ? QuestionGenerationConstants.PROMPT_KEY_DESCRIBE_IMAGE_PICTURE : taskTypeKey;
    }

    /** The same slot made as a chart, from scratch: the picture could not be painted. */
    public GenerationRequest asChart() {
        return new GenerationRequest(jobPublicId, itemIndex, taskTypeKey, cefrLevel, pool, topicHint, avoidTopics,
                null, ImageKind.CHART);
    }
}
