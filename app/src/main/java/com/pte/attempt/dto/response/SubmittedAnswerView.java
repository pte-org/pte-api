package com.pte.attempt.dto.response;

import java.util.UUID;

/**
 * attempt's canonical view of one submitted answer, joined with its pinned
 * item's frozen grading content — everything scoring (Phase 08) needs to
 * ingest and grade, without scoring opening attempt's own repositories.
 * {@code expired} mirrors {@code AttemptAnswer.expired} (a client-side-timeout
 * auto-resubmit, never a server-side deadline sweep).
 * The image travels as its media ref: the URL resolved at pin time has long
 * expired by the time a host requests scoring.
 */
public record SubmittedAnswerView(UUID answerPublicId, UUID attemptPublicId, UUID pinnedItemPublicId,
                                   UUID sessionPublicId, UUID tenantId, UUID scoreTemplatePublicId, String taskType,
                                   String payload, String correctAnswerText, String optionsJson, boolean expired,
                                   String promptText, String referenceAnswerText, UUID imagePromptRef,
                                   Integer minWordCount, Integer maxWordCount) {
}
