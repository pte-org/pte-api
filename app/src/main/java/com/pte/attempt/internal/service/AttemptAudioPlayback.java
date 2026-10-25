package com.pte.attempt.internal.service;

import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.internal.dto.response.AudioPlayResponse;
import com.pte.attempt.internal.exception.answer.NotCurrentTaskException;
import com.pte.attempt.internal.exception.answer.ReplayLimitExceededException;
import com.pte.attempt.internal.exception.content.AudioUrlExpiredException;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/** Enforces the attempt's replay policy for a user-initiated, idempotent audio play. */
class AttemptAudioPlayback {

    private final ExamAttemptRepository attemptRepository;
    private final ExamContentService contentService;
    private final Clock clock;

    AttemptAudioPlayback(ExamAttemptRepository attemptRepository, ExamContentService contentService, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
        this.clock = clock;
    }

    /** Called with {@code attempt} already locked by the lifecycle service. */
    AudioPlayResponse play(ExamAttempt attempt, UUID pinnedItemPublicId, String playRequestId) {
        // Navigation is now fully client-side — the server's currentOrderIndex is only
        // updated on answer-submit, not on every Next/Prev tap. Verifying the item
        // belongs to this attempt's snapshot is sufficient; requiring it to equal
        // currentOrderIndex incorrectly rejects valid audio requests on tasks the
        // student has navigated to without submitting the previous one.
        ExamItemView currentItem = contentService.item(attempt, pinnedItemPublicId)
                .orElseThrow(NotCurrentTaskException::new);

        if (playRequestId.equals(attempt.getLastPlayRequestId())) {
            if (Boolean.TRUE.equals(attempt.getLastPlayAllowed())) {
                return new AudioPlayResponse(currentItem.audioUrl());
            }
            throw new ReplayLimitExceededException();
        }

        if (hasExpiredAuthenticatedAudioUrl(currentItem)) {
            throw new AudioUrlExpiredException();
        }

        int limit = resolvePlayLimit(attempt);
        boolean allowed = limit < 0 || attempt.getPlayCount() < limit;
        attempt.setLastPlayRequestId(playRequestId);
        attempt.setLastPlayAllowed(allowed);
        if (!allowed) {
            attemptRepository.save(attempt);
            throw new ReplayLimitExceededException();
        }
        attempt.setPlayCount(attempt.getPlayCount() + 1);
        attemptRepository.save(attempt);
        return new AudioPlayResponse(currentItem.audioUrl());
    }

    /** UNLIMITED session policy never rejects (limit &lt; 0 sentinel). */
    private int resolvePlayLimit(ExamAttempt attempt) {
        if ("UNLIMITED".equals(attempt.getReplayPolicyType())) {
            return -1;
        }
        return attempt.getReplayPolicyLimit();
    }

    private boolean hasExpiredAuthenticatedAudioUrl(ExamItemView item) {
        Instant expiresAt = item.audioUrlExpiresAt();
        String audioUrl = item.audioUrl();
        if (expiresAt == null || audioUrl == null) {
            return false;
        }
        boolean authenticatedCloudinaryUrl = audioUrl.contains("/authenticated/")
                || audioUrl.contains("type=authenticated");
        return authenticatedCloudinaryUrl && clock.instant().isAfter(expiresAt);
    }
}
