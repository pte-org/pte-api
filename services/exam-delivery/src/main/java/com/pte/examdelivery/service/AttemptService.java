package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.config.EncryptionKeyProvider;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.event.AttemptSubmittedEvent;
import com.pte.examdelivery.domain.exception.AlreadyAttemptedException;
import com.pte.examdelivery.domain.exception.AnswerIntegrityLevelMismatchException;
import com.pte.examdelivery.domain.exception.AttemptAlreadyCompleteException;
import com.pte.examdelivery.domain.exception.AttemptNotFoundException;
import com.pte.examdelivery.domain.exception.AudioUrlExpiredException;
import com.pte.examdelivery.domain.exception.DeviceCheckRequiredException;
import com.pte.examdelivery.domain.exception.NotCurrentTaskException;
import com.pte.examdelivery.domain.exception.PinnedSnapshotEmptyException;
import com.pte.examdelivery.domain.exception.ReplayLimitExceededException;
import com.pte.examdelivery.dto.request.EncryptedSubmissionRequest;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.AudioPlayResponse;
import com.pte.examdelivery.mapper.AttemptMapper;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.ExamAttemptRepository;
import com.pte.examdelivery.repository.PinnedItemRepository;
import com.pte.examdelivery.service.cache.PinnedItemView;
import com.pte.examdelivery.service.cache.PinnedSnapshotCacheService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The attempt state machine: CREATED → IN_PROGRESS → SUBMITTED (ADR-002 —
 * SCORING/SCORED/PUBLISHED are driven by events from later phases, out of
 * scope here). Once {@link #startAttempt} returns, nothing in this class calls
 * out to authoring/scheduling again — every method after that operates purely
 * on this service's own pinned data (phase-05's central invariant).
 */
@Service
public class AttemptService {

    private final ExamAttemptRepository attemptRepository;
    private final PinnedItemRepository pinnedItemRepository;
    private final SnapshotPinService snapshotPinService;
    private final PinnedSnapshotCacheService cacheService;
    private final TimerService timerService;
    private final AnswerSubmitService answerSubmitService;
    private final AttemptMapper attemptMapper;
    private final OutboxWriter outboxWriter;
    private final EncryptionKeyProvider encryptionKeyProvider;
    private final SubmissionDecryptionService submissionDecryptionService;
    private final HeartbeatService heartbeatService;

    /**
     * No longer takes an {@code AttemptAnswerRepository} — its only caller,
     * {@code expireIfUnanswered}, was deleted along with the deadline-based
     * catch-up loop it served (client-side-exam-timer Phase 5, FR-08).
     */
    public AttemptService(ExamAttemptRepository attemptRepository, PinnedItemRepository pinnedItemRepository,
                          SnapshotPinService snapshotPinService,
                          PinnedSnapshotCacheService cacheService, TimerService timerService,
                          AnswerSubmitService answerSubmitService, AttemptMapper attemptMapper,
                          OutboxWriter outboxWriter, EncryptionKeyProvider encryptionKeyProvider,
                          SubmissionDecryptionService submissionDecryptionService, HeartbeatService heartbeatService) {
        this.attemptRepository = attemptRepository;
        this.pinnedItemRepository = pinnedItemRepository;
        this.snapshotPinService = snapshotPinService;
        this.cacheService = cacheService;
        this.timerService = timerService;
        this.answerSubmitService = answerSubmitService;
        this.attemptMapper = attemptMapper;
        this.outboxWriter = outboxWriter;
        this.encryptionKeyProvider = encryptionKeyProvider;
        this.submissionDecryptionService = submissionDecryptionService;
        this.heartbeatService = heartbeatService;
    }

    /**
     * Ownership + status check only, no task content — deliberately lighter than
     * every other method here (client-side-exam-timer Phase 2, FR-04). Independent
     * of {@code TimerService}/{@code TimerState}: recording presence must survive
     * Phase 5 deleting deadline enforcement entirely, unchanged.
     */
    @Transactional
    public void recordHeartbeat(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, caller.userId())
                .orElseThrow(AttemptNotFoundException::new);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        heartbeatService.recordHeartbeat(attempt);
    }

    @Transactional
    public AttemptTaskResponse startAttempt(StartAttemptRequest request, CurrentUser caller) {
        UUID studentPublicId = caller.userId();
        var existing = attemptRepository.findBySessionPublicIdAndStudentPublicId(request.sessionPublicId(), studentPublicId);
        if (existing.isPresent()) {
            return resumeOrReject(existing.get());
        }
        return createAndPin(request.sessionPublicId(), studentPublicId, caller.tenantId(), request.deviceCheckConfirmed());
    }

    @Transactional
    public AttemptTaskResponse getNextTask(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attemptMapper.toCompletedResponse(attempt);
        }
        return advanceUntilLiveOrComplete(attempt);
    }

    /** STANDARD-pinned attempts only — a STRICT-pinned attempt must use {@link #submitEncryptedAnswer}. */
    @Transactional
    public AttemptTaskResponse submitAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        requireIntegrityLevel(attempt, "STANDARD");
        return processAnswer(attempt, request.pinnedItemPublicId(), request.payload());
    }

    /**
     * STRICT-pinned attempts only — decrypts the per-submission AES-wrapped payload
     * with this service's own private key before funneling the plaintext through the
     * same {@link #processAnswer} path a STANDARD submission uses. The decrypted
     * plaintext is indistinguishable from a plain submission once past this point:
     * unchanged storage format, unchanged {@code AnswerSubmitted} event contract.
     */
    @Transactional
    public AttemptTaskResponse submitEncryptedAnswer(UUID attemptPublicId, EncryptedSubmissionRequest request,
                                                      CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        requireIntegrityLevel(attempt, "STRICT");
        String payload = submissionDecryptionService.decrypt(request, encryptionKeyProvider.getPrivateKey());
        return processAnswer(attempt, request.pinnedItemPublicId(), payload);
    }

    /** Request shape (plain vs. encrypted) is server-decided by the pinned level, never client-chosen (FR-03). */
    private void requireIntegrityLevel(ExamAttempt attempt, String expectedLevel) {
        if (!expectedLevel.equals(attempt.getPinnedSnapshot().getAnswerIntegrityLevel())) {
            throw new AnswerIntegrityLevelMismatchException();
        }
    }

    private AttemptTaskResponse processAnswer(ExamAttempt attempt, UUID pinnedItemPublicId, String payload) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        // Same lock as playAudio (code-reviewer HIGH finding, client-side-exam-timer
        // Phase 1): currentOrderIndex/playCount now live on the same @Version-guarded
        // ExamAttempt row playAudio locks. Without also locking here, a concurrent
        // playAudio commit between this method's unlocked read and its eventual flush
        // bumps `version` out from under this transaction, and Hibernate's dirty-check
        // UPDATE (WHERE id=? AND version=<stale>) matches zero rows ->
        // ObjectOptimisticLockingFailureException, uncaught, surfaces as a bare 500.
        // Locking here too means this transaction only ever reads the version as of
        // its own lock acquisition, never a stale one.
        attempt = lockAttempt(attempt);
        PinnedItem currentItem = currentItem(attempt);
        if (!currentItem.getPublicId().equals(pinnedItemPublicId)) {
            throw new NotCurrentTaskException();
        }

        answerSubmitService.submit(attempt, currentItem, payload);
        return advanceAfterCurrent(attempt);
    }

    @Transactional
    public AttemptTaskResponse submitAttempt(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        completeAttempt(attempt);
        return attemptMapper.toCompletedResponse(attempt);
    }

    /**
     * Idempotent per {@code playRequestId} (client-generated UUID per user-initiated
     * play tap): a repeated request with the same key replays the prior outcome
     * instead of re-incrementing {@code playCount}. The pessimistic lock — taken on
     * {@code ExamAttempt} (relocated from {@code TimerState} — client-side-exam-timer
     * Phase 1, via {@link #lockAttempt}) — serializes concurrent plays for the same
     * attempt so two requests can never both observe the same pre-increment
     * {@code playCount}. {@code processAnswer}'s advance step and
     * {@code advanceUntilLiveOrComplete} (used by {@code getNextTask}/resume) take the
     * SAME lock before touching {@code currentOrderIndex} (code-reviewer HIGH finding,
     * fixed): {@code ExamAttempt} carries {@code @Version}, unlike the old
     * {@code TimerState} row these fields used to live on — an unlocked writer racing
     * this method's locked commit would otherwise read a since-stale version and fail
     * every such race with an uncaught {@code ObjectOptimisticLockingFailureException}.
     * Locking consistently everywhere these fields are mutated means every writer only
     * ever observes the version as of its own lock acquisition, never a stale one.
     */
    @Transactional
    public AudioPlayResponse playAudio(UUID attemptPublicId, UUID pinnedItemPublicId, String playRequestId,
                                       CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        attempt = lockAttempt(attempt);
        PinnedItem currentItem = currentItem(attempt);
        if (!currentItem.getPublicId().equals(pinnedItemPublicId)) {
            throw new NotCurrentTaskException();
        }

        if (playRequestId.equals(attempt.getLastPlayRequestId())) {
            if (Boolean.TRUE.equals(attempt.getLastPlayAllowed())) {
                return new AudioPlayResponse(currentItem.getAudioUrl());
            }
            throw new ReplayLimitExceededException();
        }

        if (currentItem.getAudioUrlExpiresAt() == null || Instant.now().isAfter(currentItem.getAudioUrlExpiresAt())) {
            throw new AudioUrlExpiredException();
        }

        int limit = resolvePlayLimit(currentItem, attempt.getPinnedSnapshot());
        boolean allowed = limit < 0 || attempt.getPlayCount() < limit;
        attempt.setLastPlayRequestId(playRequestId);
        attempt.setLastPlayAllowed(allowed);
        if (!allowed) {
            attemptRepository.save(attempt);
            throw new ReplayLimitExceededException();
        }
        attempt.setPlayCount(attempt.getPlayCount() + 1);
        attemptRepository.save(attempt);
        return new AudioPlayResponse(currentItem.getAudioUrl());
    }

    /** Item override wins when present; UNLIMITED session policy (and no override) never rejects (limit &lt; 0 sentinel). */
    private int resolvePlayLimit(PinnedItem item, PinnedExamSnapshot snapshot) {
        if (item.getMaxPlayCountOverride() != null) {
            return item.getMaxPlayCountOverride();
        }
        if ("UNLIMITED".equals(snapshot.getReplayPolicyType())) {
            return -1;
        }
        return snapshot.getReplayPolicyLimit();
    }

    private AttemptTaskResponse resumeOrReject(ExamAttempt existing) {
        if (existing.getStatus() != AttemptStatus.CREATED && existing.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AlreadyAttemptedException();
        }
        return advanceUntilLiveOrComplete(existing);
    }

    private AttemptTaskResponse createAndPin(UUID sessionPublicId, UUID studentPublicId, UUID tenantId,
                                             boolean deviceCheckConfirmed) {
        ExamAttempt attempt = new ExamAttempt();
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        try {
            attempt = attemptRepository.save(attempt);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAttemptedException();
        }

        PinnedExamSnapshot pinned = snapshotPinService.pin(attempt, sessionPublicId, studentPublicId);
        if (pinned.getItems().isEmpty()) {
            throw new PinnedSnapshotEmptyException();
        }
        if (pinned.isDeviceCheckRequired() && !deviceCheckConfirmed) {
            throw new DeviceCheckRequiredException();
        }
        attempt.setPinnedSnapshot(pinned);
        attempt.setDeviceCheckPassedAt(Instant.now());
        attempt.begin();
        long totalExamSeconds = pinned.getItems().stream()
                .mapToLong(item -> (long) item.getPrepSeconds() + item.getResponseSeconds())
                .sum();
        attempt.setExamEndTime(attempt.getStartedAt().plusSeconds(totalExamSeconds));
        String encryptionPublicKey = "STRICT".equals(pinned.getAnswerIntegrityLevel())
                ? encryptionKeyProvider.getPublicKeyBase64()
                : null;
        attempt = attemptRepository.save(attempt);

        // attempt already has an id at this point (saved above), so this
        // save() cascades via merge(), not persist() — merge() returns a
        // NEW managed copy of the graph and never assigns generated ids
        // (publicId included) onto the original `pinned`/`PinnedItem`
        // instances we built. Re-read the saved copy off the returned
        // `attempt` instead of the stale local `pinned` reference.
        PinnedExamSnapshot savedPinned = attempt.getPinnedSnapshot();
        List<PinnedItemView> views = savedPinned.getItems().stream().map(PinnedSnapshotCacheService::toView).toList();
        cacheService.warm(savedPinned.getPublicId(), views);

        PinnedItemView first = views.get(0);
        timerService.startTask(attempt, first, views);
        int effectivePrep = timerService.resolveEffectivePrepSeconds(first);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, first, views);
        return attemptMapper.toTaskResponse(attempt, first, effectivePrep, effectiveResponse, views.size(), encryptionPublicKey);
    }

    /**
     * No expiry sweep (client-side-exam-timer Phase 5) — {@code currentOrderIndex}
     * is already correct as maintained by whichever call last advanced it
     * ({@link #createAndPin}/{@link #advanceAfterCurrent}), so this is a pure read,
     * needing no lock (nothing here mutates {@code attempt}). Deleting the former
     * deadline-based catch-up loop removes the "forgiveness" it gave a
     * currentOrderIndex that had drifted out of sync for an unrelated reason — any
     * remaining drift now surfaces directly as a data-integrity bug at its source,
     * rather than being silently patched over here (Phase 5 plan, accepted MEDIUM risk).
     */
    private AttemptTaskResponse advanceUntilLiveOrComplete(ExamAttempt attempt) {
        long totalItems = pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId());
        List<PinnedItemView> allItems = allItemViews(attempt);
        PinnedItemView currentView = PinnedSnapshotCacheService.toView(currentItem(attempt));
        int effectivePrep = timerService.resolveEffectivePrepSeconds(currentView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, currentView, allItems);
        return attemptMapper.toTaskResponse(attempt, currentView, effectivePrep, effectiveResponse, (int) totalItems);
    }

    private AttemptTaskResponse advanceAfterCurrent(ExamAttempt attempt) {
        long totalItems = pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId());
        int nextIndex = attempt.getCurrentOrderIndex() + 1;
        if (nextIndex >= totalItems) {
            completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }
        List<PinnedItemView> allItems = allItemViews(attempt);
        PinnedItemView nextView = PinnedSnapshotCacheService.toView(itemAt(attempt, nextIndex));
        timerService.startTask(attempt, nextView, allItems);
        int effectivePrep = timerService.resolveEffectivePrepSeconds(nextView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, nextView, allItems);
        return attemptMapper.toTaskResponse(attempt, nextView, effectivePrep, effectiveResponse, (int) totalItems);
    }

    private List<PinnedItemView> allItemViews(ExamAttempt attempt) {
        return attempt.getPinnedSnapshot().getItems().stream().map(PinnedSnapshotCacheService::toView).toList();
    }

    private void completeAttempt(ExamAttempt attempt) {
        // Locked here (not just at each caller) so every path to completing an
        // attempt — including submitAttempt, which had no other lock on this
        // @Version-guarded row — is covered by a single choke point (code-reviewer
        // 2nd-pass HIGH finding). Callers that already locked earlier in the same
        // transaction (advanceUntilLiveOrComplete, processAnswer's advanceAfterCurrent)
        // just re-acquire their own already-held lock — a cheap, harmless no-op, not
        // a double-lock hazard (same transaction, same row).
        lockAttempt(attempt);
        attempt.submit();
        attemptRepository.save(attempt);
        outboxWriter.write(ExamDeliveryConstants.AGGREGATE_ATTEMPT, attempt.getPublicId().toString(),
                ExamDeliveryConstants.EVENT_ATTEMPT_SUBMITTED,
                new AttemptSubmittedEvent(attempt.getPublicId(), attempt.getSessionPublicId(),
                        attempt.getStudentPublicId(), attempt.getTenantId()),
                attempt.getTenantId());
    }

    private PinnedItem currentItem(ExamAttempt attempt) {
        return itemAt(attempt, attempt.getCurrentOrderIndex());
    }

    private PinnedItem itemAt(ExamAttempt attempt, int orderIndex) {
        return pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), orderIndex)
                .orElseThrow(() -> new IllegalStateException("Missing pinned item at index " + orderIndex));
    }

    ExamAttempt findOwned(UUID publicId, UUID studentPublicId) {
        return attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(AttemptNotFoundException::new);
    }

    /**
     * Re-fetches {@code attempt} under a pessimistic write lock, by id, within the
     * same persistence context — returns the SAME managed instance (JPA identity map),
     * so already-loaded lazy associations (e.g. {@code pinnedSnapshot}) stay populated.
     * Every mutator of the relocated {@code currentOrderIndex}/{@code playCount}/
     * {@code lastPlayRequestId}/{@code lastPlayAllowed} fields must call this first
     * (client-side-exam-timer Phase 1 — see {@link #playAudio}'s doc comment for why).
     */
    private ExamAttempt lockAttempt(ExamAttempt attempt) {
        Long attemptId = attempt.getId();
        return attemptRepository.findWithLockById(attemptId)
                .orElseThrow(() -> new IllegalStateException("Attempt disappeared mid-transaction: " + attemptId));
    }
}
