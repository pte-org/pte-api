package com.pte.attempt.dto.response;

import com.pte.itembank.TaskRuntimeProfileDescriptor;

import java.time.Instant;
import java.util.UUID;

/**
 * attempt's canonical view of one submitted answer, joined with its exam version
 * item's frozen grading content — everything scoring (Phase 08) needs to
 * ingest and grade, without scoring opening attempt's own repositories.
 * {@code expired} mirrors {@code AttemptAnswer.expired} (a client-side-timeout
 * auto-resubmit, never a server-side deadline sweep).
 * {@code section} and {@code runtime} come from the exam item so scoring can pick its method without the
 * exam definition; the {@code selected*} fields are the host's source choice stored on the answer itself.
 * The image travels as its media ref: the URL resolved at pin time has long
 * expired by the time a host requests scoring.
 */
public record SubmittedAnswerView(UUID answerPublicId, UUID attemptPublicId, UUID itemPublicId,
                                   UUID sessionPublicId, UUID tenantId, UUID examDefinitionPublicId, String taskType,
                                   String payload, String correctAnswerText, String optionsJson, boolean expired,
                                   String promptText, String referenceAnswerText, UUID imagePromptRef,
                                   Integer minWordCount, Integer maxWordCount, String section,
                                   TaskRuntimeProfileDescriptor runtime, String selectedScoreSource,
                                   Instant selectedScoreSourceAt, UUID selectedScoreSourceByPublicId,
                                   UUID selectedEvaluationPublicId) {
}
