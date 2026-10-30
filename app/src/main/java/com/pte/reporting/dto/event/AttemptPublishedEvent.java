package com.pte.reporting.dto.event;

import java.util.UUID;

/**
 * Published after a host's publish command marks an {@code ExamResult}
 * visible, inside the same transaction that commits it — {@code
 * notification} listens via {@code @TransactionalEventListener} (fires only
 * after commit succeeds), to email the student their scores. Students have no
 * other way to read a score, so the event carries the published scores itself.
 * A null score means the skill had insufficient data, never a score of zero.
 */
public record AttemptPublishedEvent(UUID attemptPublicId, UUID sessionPublicId, UUID studentPublicId, UUID tenantId,
        Integer overallScore, Integer listeningScore, Integer readingScore, Integer speakingScore,
        Integer writingScore) {
}
