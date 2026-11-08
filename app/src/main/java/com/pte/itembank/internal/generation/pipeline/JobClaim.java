package com.pte.itembank.internal.generation.pipeline;

import com.pte.itembank.domain.enums.CefrLevel;
import com.pte.itembank.domain.enums.QuestionPool;

import java.util.UUID;

/** A job a worker owns (until its lease lapses): everything the pipeline needs, copied out of the entity. */
public record JobClaim(Long jobId, UUID jobPublicId, UUID requesterId, String taskTypeKey, CefrLevel cefrLevel,
                       QuestionPool pool, String topicHint, String workerId, boolean saveQuestions) {

    /** A job that saves its questions, the usual case. */
    public JobClaim(Long jobId, UUID jobPublicId, UUID requesterId, String taskTypeKey, CefrLevel cefrLevel,
                    QuestionPool pool, String topicHint, String workerId) {
        this(jobId, jobPublicId, requesterId, taskTypeKey, cefrLevel, pool, topicHint, workerId, true);
    }
}
