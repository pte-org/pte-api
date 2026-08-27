package com.pte.examdelivery.service;

import com.pte.common.security.CurrentUser;
import com.pte.examdelivery.constant.ExamDeliveryConstants;
import com.pte.examdelivery.domain.ExamAttempt;
import com.pte.examdelivery.domain.PinnedExamSnapshot;
import com.pte.examdelivery.domain.PinnedItem;
import com.pte.examdelivery.domain.TimerState;
import com.pte.examdelivery.domain.enums.AttemptStatus;
import com.pte.examdelivery.domain.event.AttemptSubmittedEvent;
import com.pte.examdelivery.domain.exception.AlreadyAttemptedException;
import com.pte.examdelivery.domain.exception.AttemptAlreadyCompleteException;
import com.pte.examdelivery.domain.exception.AttemptNotFoundException;
import com.pte.examdelivery.domain.exception.AttemptNotInProgressException;
import com.pte.examdelivery.domain.exception.AudioUrlExpiredException;
import com.pte.examdelivery.domain.exception.DeviceCheckRequiredException;
import com.pte.examdelivery.domain.exception.NotCurrentTaskException;
import com.pte.examdelivery.domain.exception.PinnedSnapshotEmptyException;
import com.pte.examdelivery.domain.exception.ReplayLimitExceededException;
import com.pte.examdelivery.domain.exception.ResponseWindowExpiredException;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
import com.pte.examdelivery.dto.response.AudioPlayResponse;
import com.pte.examdelivery.dto.response.TimerStateResponse;
import com.pte.examdelivery.mapper.AttemptMapper;
import com.pte.examdelivery.messaging.outbox.OutboxWriter;
import com.pte.examdelivery.repository.AttemptAnswerRepository;
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
    private final AttemptAnswerRepository attemptAnswerRepository;
    private final SnapshotPinService snapshotPinService;
    private final PinnedSnapshotCacheService cacheService;
    private final TimerService timerService;
    private final AnswerSubmitService answerSubmitService;
    private final AttemptMapper attemptMapper;
    private final OutboxWriter outboxWriter;

    public AttemptService(ExamAttemptRepository attemptRepository, PinnedItemRepository pinnedItemRepository,
                          AttemptAnswerRepository attemptAnswerRepository, SnapshotPinService snapshotPinService,
                          PinnedSnapshotCacheService cacheService, TimerService timerService,
                          AnswerSubmitService answerSubmitService, AttemptMapper attemptMapper,
                          OutboxWriter outboxWriter) {
        this.attemptRepository = attemptRepository;
        this.pinnedItemRepository = pinnedItemRepository;
        this.attemptAnswerRepository = attemptAnswerRepository;
        this.snapshotPinService = snapshotPinService;
        this.cacheService = cacheService;
        this.timerService = timerService;
        this.answerSubmitService = answerSubmitService;
        this.attemptMapper = attemptMapper;
        this.outboxWriter = outboxWriter;
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

    @Transactional
    public AttemptTaskResponse submitAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        TimerState timer = timerService.getState(attempt.getId());
        PinnedItem currentItem = currentItem(attempt, timer);
        if (!currentItem.getPublicId().equals(request.pinnedItemPublicId())) {
            throw new NotCurrentTaskException();
        }
        if (timerService.isResponseWindowExpired(timer)) {
            expireIfUnanswered(attempt, currentItem);
            throw new ResponseWindowExpiredException();
        }

        answerSubmitService.submit(attempt, currentItem, request.payload());
        return advanceAfterCurrent(attempt, timer);
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
     * instead of re-incrementing {@code playCount}. The pessimistic lock on
     * {@code TimerState} serializes concurrent plays for the same attempt so two
     * requests can never both observe the same pre-increment {@code playCount}.
     */
    @Transactional
    public AudioPlayResponse playAudio(UUID attemptPublicId, UUID pinnedItemPublicId, String playRequestId,
                                       CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        TimerState timer = timerService.getStateWithLock(attempt.getId());
        PinnedItem currentItem = currentItem(attempt, timer);
        if (!currentItem.getPublicId().equals(pinnedItemPublicId)) {
            throw new NotCurrentTaskException();
        }

        if (playRequestId.equals(timer.getLastPlayRequestId())) {
            if (Boolean.TRUE.equals(timer.getLastPlayAllowed())) {
                return new AudioPlayResponse(currentItem.getAudioUrl());
            }
            throw new ReplayLimitExceededException();
        }

        if (currentItem.getAudioUrlExpiresAt() == null || Instant.now().isAfter(currentItem.getAudioUrlExpiresAt())) {
            throw new AudioUrlExpiredException();
        }

        int limit = resolvePlayLimit(currentItem, attempt.getPinnedSnapshot());
        boolean allowed = limit < 0 || timer.getPlayCount() < limit;
        timer.setLastPlayRequestId(playRequestId);
        timer.setLastPlayAllowed(allowed);
        if (!allowed) {
            timerService.save(timer);
            throw new ReplayLimitExceededException();
        }
        timer.setPlayCount(timer.getPlayCount() + 1);
        timerService.save(timer);
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

    /** Lightweight poll target for the client's countdown UI — no task content, just deadlines. */
    @Transactional(readOnly = true)
    public TimerStateResponse getTimerState(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        return attemptMapper.toTimerResponse(attempt, timerService.getState(attempt.getId()));
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
        TimerState timer = timerService.startTaskTimer(attempt, first, views);
        return attemptMapper.toTaskResponse(attempt, first, timer, views.size());
    }

    private AttemptTaskResponse advanceUntilLiveOrComplete(ExamAttempt attempt) {
        TimerState timer = timerService.getState(attempt.getId());
        long totalItems = pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId());
        List<PinnedItemView> allItems = allItemViews(attempt);

        while (timerService.isResponseWindowExpired(timer)) {
            PinnedItem currentItem = currentItem(attempt, timer);
            expireIfUnanswered(attempt, currentItem);

            int nextIndex = timer.getCurrentOrderIndex() + 1;
            if (nextIndex >= totalItems) {
                completeAttempt(attempt);
                return attemptMapper.toCompletedResponse(attempt);
            }
            PinnedItem nextItem = itemAt(attempt, nextIndex);
            timer = timerService.startTaskTimer(attempt, PinnedSnapshotCacheService.toView(nextItem), allItems);
        }

        PinnedItem current = currentItem(attempt, timer);
        return attemptMapper.toTaskResponse(attempt, PinnedSnapshotCacheService.toView(current), timer, (int) totalItems);
    }

    private AttemptTaskResponse advanceAfterCurrent(ExamAttempt attempt, TimerState timer) {
        long totalItems = pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId());
        int nextIndex = timer.getCurrentOrderIndex() + 1;
        if (nextIndex >= totalItems) {
            completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }
        PinnedItem nextItem = itemAt(attempt, nextIndex);
        TimerState nextTimer = timerService.startTaskTimer(attempt, PinnedSnapshotCacheService.toView(nextItem), allItemViews(attempt));
        return attemptMapper.toTaskResponse(attempt, PinnedSnapshotCacheService.toView(nextItem), nextTimer, (int) totalItems);
    }

    private List<PinnedItemView> allItemViews(ExamAttempt attempt) {
        return attempt.getPinnedSnapshot().getItems().stream().map(PinnedSnapshotCacheService::toView).toList();
    }

    private void expireIfUnanswered(ExamAttempt attempt, PinnedItem item) {
        boolean alreadyAnswered = attemptAnswerRepository
                .findByAttemptIdAndPinnedItemId(attempt.getId(), item.getId()).isPresent();
        if (!alreadyAnswered) {
            answerSubmitService.autoExpire(attempt, item);
        }
    }

    private void completeAttempt(ExamAttempt attempt) {
        attempt.submit();
        attemptRepository.save(attempt);
        outboxWriter.write(ExamDeliveryConstants.AGGREGATE_ATTEMPT, attempt.getPublicId().toString(),
                ExamDeliveryConstants.EVENT_ATTEMPT_SUBMITTED,
                new AttemptSubmittedEvent(attempt.getPublicId(), attempt.getSessionPublicId(),
                        attempt.getStudentPublicId(), attempt.getTenantId()),
                attempt.getTenantId());
    }

    private PinnedItem currentItem(ExamAttempt attempt, TimerState timer) {
        return itemAt(attempt, timer.getCurrentOrderIndex());
    }

    private PinnedItem itemAt(ExamAttempt attempt, int orderIndex) {
        return pinnedItemRepository.findByPinnedSnapshotIdAndOrderIndex(attempt.getPinnedSnapshot().getId(), orderIndex)
                .orElseThrow(() -> new IllegalStateException("Missing pinned item at index " + orderIndex));
    }

    ExamAttempt findOwned(UUID publicId, UUID studentPublicId) {
        return attemptRepository.findWithPinnedByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(AttemptNotFoundException::new);
    }
}
