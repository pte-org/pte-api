package com.pte.practice.internal.service;

import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.scoring.PracticeScoringException;
import com.pte.scoring.PracticeScoringFailure;
import com.pte.scoring.PracticeScoringRequest;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Builds the exam-shaped scoring request from the content a practice item pinned when its session started, so a
 * later edit to the question never changes how an answer is scored. Shared by the async worker and the inline
 * objective path.
 */
@Component
public class PracticeScoringRequestFactory {

    private final PracticeAnswerCanonicalizer canonicalizer;

    public PracticeScoringRequestFactory(PracticeAnswerCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    /** @throws PracticeScoringException INVALID_PAYLOAD when the saved answer has no scoreable form */
    public PracticeScoringRequest build(PracticeSessionItem item, UUID tenantId) {
        String payload = canonicalizer.canonicalize(item.getRendererKey(), item.getSavedPayload(),
                item.getObjectiveOptionsJson()).orElseThrow(
                        () -> new PracticeScoringException(PracticeScoringFailure.INVALID_PAYLOAD));
        return new PracticeScoringRequest(item.getTaskCode(), payload, item.getPromptText(),
                item.getReferenceAnswerText(), item.getCorrectAnswerText(), item.getImagePromptRef(),
                item.getMinWordCount(), item.getMaxWordCount(), item.getObjectiveOptionsJson(), tenantId);
    }
}
