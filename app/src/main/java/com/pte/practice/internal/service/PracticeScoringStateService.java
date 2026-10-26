package com.pte.practice.internal.service;

import com.pte.practice.internal.config.PracticeScoringProperties;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeScoringStatus;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.messaging.PracticeScoringDispatcher;
import com.pte.practice.internal.messaging.PracticeScoringJob;
import com.pte.practice.internal.repository.PracticeResponseRepository;
import com.pte.scoring.PracticeAiEvaluation;
import com.pte.scoring.PracticeScoringFailure;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the scoring lifecycle of one practice item (NONE, SCORING, SCORED, FAILED). A result is written only for the
 * item's current dispatch token, a SCORED item is final, and only a FAILED item with attempts left can be re-submitted.
 * The caller's transaction carries {@link #startScoring}, {@link #recover} and {@link #retry}; the worker's writes
 * ({@link #complete}, {@link #fail}) each run in their own transaction.
 */
@Service
public class PracticeScoringStateService {

    static final String FAILURE_TIMED_OUT = "TIMED_OUT";
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);

    private final PracticeResponseRepository itemRepository;
    private final PracticeScoringDispatcher dispatcher;
    private final PracticeScoringProperties properties;
    private final JsonMapper jsonMapper;

    public PracticeScoringStateService(PracticeResponseRepository itemRepository,
            PracticeScoringDispatcher dispatcher, PracticeScoringProperties properties, JsonMapper jsonMapper) {
        this.itemRepository = itemRepository;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    /** First submission of an AI-scored answer: enforce the rate limit, mark SCORING and queue the job after commit. */
    public void startScoring(PracticeSession session, PracticeResponse item, Instant now) {
        begin(session, item, now, 1);
    }

    private void begin(PracticeSession session, PracticeResponse item, Instant now, int attempts) {
        requireWithinRateLimit(session, now);
        item.setScoringRequestedAt(now);
        item.setScoringAttempts(attempts);
        item.setScoringRedispatchCount(0);
        item.setScoringFailureCode(null);
        dispatch(session, item, now);
    }

    /** Stores a result computed inside the answer call (objective types); the item is final immediately. */
    public void recordInline(PracticeResponse item, PracticeAiEvaluation evaluation, Instant now) {
        applyResult(item, evaluation);
        item.setObjectiveScore(evaluation.rawScore());
        item.setObjectiveEvaluatedAt(now);
        finalise(item);
    }

    /**
     * Repairs an item whose job was lost or never finished: past the max age it becomes FAILED, past the redispatch
     * threshold it is re-sent once with a new token. Runs on every poll and read, so no sweeper is needed.
     */
    public void recover(PracticeSession session, PracticeResponse item, Instant now) {
        if (item.getScoringStatus() != PracticeScoringStatus.SCORING) {
            return;
        }
        Instant requestedAt = item.getScoringRequestedAt();
        if (requestedAt != null
                && Duration.between(requestedAt, now).getSeconds() > properties.getMaxAgeSeconds()) {
            markFailed(item, FAILURE_TIMED_OUT);
            itemRepository.saveAndFlush(item);
            return;
        }
        Instant dispatchedAt = item.getScoringDispatchedAt();
        if (dispatchedAt != null && item.getScoringRedispatchCount() < 1
                && Duration.between(dispatchedAt, now).getSeconds() > properties.getRedispatchAfterSeconds()) {
            item.setScoringRedispatchCount(item.getScoringRedispatchCount() + 1);
            dispatch(session, item, now);
            itemRepository.saveAndFlush(item);
        }
    }

    /** Re-submits a FAILED item. A second call while it is SCORING just returns the current state. */
    public PracticeItemResultResponse retry(PracticeSession session, PracticeResponse item, Instant now) {
        if (item.getScoringStatus() == PracticeScoringStatus.SCORING) {
            return resultOf(item);
        }
        if (!isRetryable(item)) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_SCORING_RETRY_NOT_ALLOWED,
                    PracticeConstants.PRACTICE_SCORING_RETRY_NOT_ALLOWED_MESSAGE);
        }
        begin(session, item, now, item.getScoringAttempts() + 1);
        itemRepository.saveAndFlush(item);
        return resultOf(item);
    }

    /**
     * The worker takes a job before it spends vendor money: if the item still awaits exactly this token, the token is
     * rotated and the item returned, so a redelivered copy of the same job finds a different token and is skipped.
     * The rotated token (and dispatch time) also restarts the re-dispatch window from when work really began.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PracticeResponse claim(UUID itemPublicId, UUID jobToken, Instant now) {
        PracticeResponse item = awaiting(itemPublicId, jobToken);
        if (item == null) {
            return null;
        }
        item.setScoringDispatchToken(UUID.randomUUID());
        item.setScoringDispatchedAt(now);
        itemRepository.saveAndFlush(item);
        return item;
    }

    /**
     * Worker write for a successful score. Returns false without writing when the item no longer awaits this token
     * (stale redelivery, re-dispatch, already final); a concurrent write surfaces as an optimistic-lock failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean complete(UUID itemPublicId, UUID dispatchToken, PracticeAiEvaluation evaluation) {
        PracticeResponse item = awaiting(itemPublicId, dispatchToken);
        if (item == null) {
            return false;
        }
        applyResult(item, evaluation);
        finalise(item);
        itemRepository.saveAndFlush(item);
        return true;
    }

    /** Worker write for a provider or internal failure, under the same token rule as {@link #complete}. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(UUID itemPublicId, UUID dispatchToken, PracticeScoringFailure failure) {
        PracticeResponse item = awaiting(itemPublicId, dispatchToken);
        if (item == null) {
            return false;
        }
        markFailed(item, failure.name());
        itemRepository.saveAndFlush(item);
        return true;
    }

    /** The item if it is still SCORING for exactly this dispatch token, else null. */
    private PracticeResponse awaiting(UUID itemPublicId, UUID dispatchToken) {
        if (itemPublicId == null || dispatchToken == null) {
            return null;
        }
        return itemRepository.findByPublicIdAndDeletedFalse(itemPublicId)
                .filter(item -> item.getScoringStatus() == PracticeScoringStatus.SCORING
                        && dispatchToken.equals(item.getScoringDispatchToken()))
                .orElse(null);
    }

    public PracticeItemResultResponse resultOf(PracticeResponse item) {
        PracticeScoringStatus state = item.getScoringStatus();
        boolean scored = state == PracticeScoringStatus.SCORED;
        boolean failed = state == PracticeScoringStatus.FAILED;
        return new PracticeItemResultResponse(item.getPublicId(), item.getTaskCode(), state,
                scored ? scoreOf(item) : null,
                scored ? item.getAiScoreStatus() : null,
                scored ? readSubScores(jsonMapper, item.getAiSubScoresJson()) : Map.of(),
                scored ? item.getAiFeedback() : null,
                scored ? item.getAiTranscript() : null,
                failed ? item.getScoringFailureCode() : null,
                isRetryable(item),
                scored ? item.getAiScoredAt() : null);
    }

    /** Objective items fill both result sets; fall back to the objective score if the AI column is empty. */
    private static Integer scoreOf(PracticeResponse item) {
        return item.getAiScore() != null ? item.getAiScore() : item.getObjectiveScore();
    }

    private boolean isRetryable(PracticeResponse item) {
        if (item.getScoringStatus() != PracticeScoringStatus.FAILED
                || item.getScoringAttempts() >= 1 + properties.getMaxRetries()) {
            return false;
        }
        String code = item.getScoringFailureCode();
        if (FAILURE_TIMED_OUT.equals(code)) {
            return true;
        }
        try {
            return code != null && PracticeScoringFailure.valueOf(code).isRetryable();
        } catch (IllegalArgumentException unknownCode) {
            return false;
        }
    }

    private void dispatch(PracticeSession session, PracticeResponse item, Instant now) {
        UUID token = UUID.randomUUID();
        item.setScoringStatus(PracticeScoringStatus.SCORING);
        item.setScoringDispatchToken(token);
        item.setScoringDispatchedAt(now);
        dispatcher.dispatchAfterCommit(new PracticeScoringJob(item.getPublicId(), session.getTenantId(), token));
    }

    private void requireWithinRateLimit(PracticeSession session, Instant now) {
        long recent = itemRepository.countScoringRequestsSince(session.getStudentPublicId(),
                now.minus(RATE_LIMIT_WINDOW));
        if (recent >= properties.getRateLimitPerMinute()) {
            throw new PracticeSessionException(HttpStatus.TOO_MANY_REQUESTS,
                    PracticeConstants.PRACTICE_SCORING_RATE_LIMITED,
                    PracticeConstants.PRACTICE_SCORING_RATE_LIMITED_MESSAGE);
        }
    }

    private void markFailed(PracticeResponse item, String failureCode) {
        item.setScoringStatus(PracticeScoringStatus.FAILED);
        item.setScoringFailureCode(failureCode);
        item.setScoringDispatchToken(null);
    }

    private void finalise(PracticeResponse item) {
        item.setScoringStatus(PracticeScoringStatus.SCORED);
        item.setScoringFailureCode(null);
        item.setScoringDispatchToken(null);
    }

    private void applyResult(PracticeResponse item, PracticeAiEvaluation evaluation) {
        item.setAiScore(evaluation.rawScore());
        item.setAiScoreStatus(evaluation.scoreStatus());
        item.setAiSubScoresJson(writeSubScores(evaluation.subScores()));
        item.setAiProvider(evaluation.provider());
        item.setAiModel(evaluation.model());
        item.setAiProviderVersion(evaluation.providerVersion());
        item.setAiTranscript(evaluation.transcript());
        item.setAiDetailJson(evaluation.detailJson());
        item.setAiFeedback(evaluation.feedback());
        item.setAiScoredAt(evaluation.evaluatedAt());
        item.setAiResultHash(evaluation.resultHash());
    }

    private String writeSubScores(Map<String, Integer> subScores) {
        try {
            return jsonMapper.writeValueAsString(subScores == null ? Map.of() : subScores);
        } catch (RuntimeException exception) {
            return "{}";
        }
    }

    /** Numeric entries of the stored sub-score object; anything unreadable yields no sub-scores. */
    static Map<String, Integer> readSubScores(JsonMapper jsonMapper, String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonNode root = jsonMapper.readTree(json);
            if (root == null || !root.isObject()) {
                return Map.of();
            }
            Map<String, Integer> scores = new LinkedHashMap<>();
            root.properties().forEach(entry -> {
                if (entry.getValue() != null && entry.getValue().isNumber()) {
                    scores.put(entry.getKey(), entry.getValue().asInt());
                }
            });
            return Map.copyOf(scores);
        } catch (RuntimeException exception) {
            return Map.of();
        }
    }
}
