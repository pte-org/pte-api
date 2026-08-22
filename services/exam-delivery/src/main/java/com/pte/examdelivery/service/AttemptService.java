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
import com.pte.examdelivery.domain.exception.NotCurrentTaskException;
import com.pte.examdelivery.domain.exception.PinnedSnapshotEmptyException;
import com.pte.examdelivery.domain.exception.ResponseWindowExpiredException;
import com.pte.examdelivery.dto.request.StartAttemptRequest;
import com.pte.examdelivery.dto.request.SubmitAnswerRequest;
import com.pte.examdelivery.dto.response.AttemptTaskResponse;
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
        return createAndPin(request.sessionPublicId(), studentPublicId, caller.tenantId());
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

    /** Lightweight poll target for the client's countdown UI — no task content, just deadlines. */
    @Transactional(readOnly = true)
    public TimerStateResponse getTimerState(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller.userId());
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        return attemptMapper.toTimerResponse(timerService.getState(attempt.getId()));
    }

    private AttemptTaskResponse resumeOrReject(ExamAttempt existing) {
        if (existing.getStatus() != AttemptStatus.CREATED && existing.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AlreadyAttemptedException();
        }
        return advanceUntilLiveOrComplete(existing);
    }

    private AttemptTaskResponse createAndPin(UUID sessionPublicId, UUID studentPublicId, UUID tenantId) {
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
        attempt.setPinnedSnapshot(pinned);
        attempt.begin();
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
        TimerState timer = timerService.startTaskTimer(attempt, first);
        return attemptMapper.toTaskResponse(attempt, first, timer, views.size());
    }

    private AttemptTaskResponse advanceUntilLiveOrComplete(ExamAttempt attempt) {
        TimerState timer = timerService.getState(attempt.getId());
        long totalItems = pinnedItemRepository.countByPinnedSnapshotId(attempt.getPinnedSnapshot().getId());

        while (timerService.isResponseWindowExpired(timer)) {
            PinnedItem currentItem = currentItem(attempt, timer);
            expireIfUnanswered(attempt, currentItem);

            int nextIndex = timer.getCurrentOrderIndex() + 1;
            if (nextIndex >= totalItems) {
                completeAttempt(attempt);
                return attemptMapper.toCompletedResponse(attempt);
            }
            PinnedItem nextItem = itemAt(attempt, nextIndex);
            timer = timerService.startTaskTimer(attempt, PinnedSnapshotCacheService.toView(nextItem));
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
        TimerState nextTimer = timerService.startTaskTimer(attempt, PinnedSnapshotCacheService.toView(nextItem));
        return attemptMapper.toTaskResponse(attempt, PinnedSnapshotCacheService.toView(nextItem), nextTimer, (int) totalItems);
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
