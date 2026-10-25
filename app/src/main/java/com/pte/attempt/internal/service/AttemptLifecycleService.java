package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.dto.request.EncryptedSubmissionRequest;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.request.NavigateTaskRequest;
import com.pte.attempt.internal.dto.request.SubmitAnswerRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.dto.response.AudioPlayResponse;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.shared.security.CurrentUser;
import com.pte.session.SessionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The attempt state machine: CREATED → IN_PROGRESS → SUBMITTED (SCORING/
 * SCORED/PUBLISHED are driven by application events from later phases, out
 * of scope here). Once {@link #startAttempt} returns, nothing in this class
 * calls another module again — every method after that operates purely on
 * this module's own data and the cached exam content (Phase 07's central invariant).
 *
 * <p>This bean owns every transaction boundary; the package-private
 * collaborators it creates are plain objects running inside those transactions.
 */
@Service
public class AttemptLifecycleService {

    private final ExamAttemptRepository attemptRepository;
    private final AttemptMapper attemptMapper;
    private final EncryptionKeyProvider encryptionKeyProvider;
    private final SubmissionDecryptionService submissionDecryptionService;
    private final HeartbeatService heartbeatService;
    private final CapabilityNegotiationService capabilityNegotiationService;
    private final AttemptAccess access;
    private final AttemptTaskProgression progression;
    private final AttemptStartFlow startFlow;
    private final AttemptAnswerRecorder answerRecorder;
    private final AttemptAudioPlayback audioPlayback;

    public AttemptLifecycleService(ExamAttemptRepository attemptRepository, ExamContentService contentService,
                          TimerService timerService, AnswerSubmitService answerSubmitService,
                          AttemptMapper attemptMapper, EncryptionKeyProvider encryptionKeyProvider,
                          SubmissionDecryptionService submissionDecryptionService, HeartbeatService heartbeatService,
                          CapabilityNegotiationService capabilityNegotiationService,
                          SessionService sessionService,
            Clock clock) {
        this.attemptRepository = attemptRepository;
        this.attemptMapper = attemptMapper;
        this.encryptionKeyProvider = encryptionKeyProvider;
        this.submissionDecryptionService = submissionDecryptionService;
        this.heartbeatService = heartbeatService;
        this.capabilityNegotiationService = capabilityNegotiationService;
        this.access = new AttemptAccess(attemptRepository, sessionService, clock);
        this.progression = new AttemptTaskProgression(attemptRepository, contentService, timerService,
                answerSubmitService, attemptMapper, encryptionKeyProvider, capabilityNegotiationService, access);
        this.startFlow = new AttemptStartFlow(attemptRepository, contentService, timerService, attemptMapper,
                encryptionKeyProvider, capabilityNegotiationService, sessionService, access, progression, clock);
        this.answerRecorder = new AttemptAnswerRecorder(contentService, answerSubmitService, access, progression);
        this.audioPlayback = new AttemptAudioPlayback(attemptRepository, contentService, clock);
    }

    @Transactional(readOnly = true)
    public AttemptPreflightResponse preflight(AttemptPreflightRequest request, CurrentUser caller) {
        return capabilityNegotiationService.preflight(request, caller);
    }

    /**
     * Ownership + status check only, no task content — deliberately lighter
     * than every other method here. Independent of {@link TimerService}:
     * recording presence must stay unaffected by anything else happening to
     * an attempt.
     */
    @Transactional
    public void recordHeartbeat(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = attemptRepository.findByPublicIdAndStudentPublicId(attemptPublicId, caller.userId())
                .orElseThrow(AttemptNotFoundException::new);
        access.requireTenantOwnership(attempt, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        heartbeatService.recordHeartbeat(attempt);
    }

    @Transactional
    public AttemptTaskResponse startAttempt(StartAttemptRequest request, CurrentUser caller) {
        return startFlow.startAttempt(request, caller);
    }

    /**
     * Returns every task of the attempt's exam in order. The client
     * uses this to prefetch the full list on start/resume and navigate locally
     * without a server round-trip per step. Timer values are computed at call
     * time: speaking/listening items get their own {@code prepSeconds}/
     * {@code responseSeconds}; reading items get {@code sectionBudget - elapsed}
     * (live at this instant), which the client ignores in favour of the global
     * exam timer already derived from {@code examEndTime}.
     */
    @Transactional
    public List<AttemptTaskResponse> getAllTasks(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return List.of(attemptMapper.toCompletedResponse(attempt));
        }
        return progression.allTasks(attempt, caller);
    }

    @Transactional
    public AttemptTaskResponse getNextTask(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attemptMapper.toCompletedResponse(attempt);
        }
        return progression.advanceUntilLiveOrComplete(attempt, caller);
    }

    /**
     * Moves the live task pointer by one item without submitting an answer.
     * Both adjacent sections are checked server-side;
     * the client-provided item id is only a stale-screen guard.
     */
    @Transactional
    public AttemptTaskResponse navigateTask(UUID attemptPublicId, NavigateTaskRequest request, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attemptMapper.toCompletedResponse(attempt);
        }
        return progression.navigate(attempt, request, caller);
    }

    /** STANDARD attempts only — a STRICT attempt must use {@link #submitEncryptedAnswer}. */
    @Transactional
    public AttemptTaskResponse submitAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        answerRecorder.requireIntegrityLevel(attempt, "STANDARD");
        return answerRecorder.processAnswer(attempt, request.pinnedItemPublicId(), request.payload(),
                request.confidence(), caller);
    }

    /** Persists an answer draft while leaving the current task pointer unchanged. */
    @Transactional
    public AttemptTaskResponse saveAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        answerRecorder.requireIntegrityLevel(attempt, "STANDARD");
        return answerRecorder.saveAnswerWithoutAdvance(attempt, request.pinnedItemPublicId(), request.payload(),
                request.confidence(), caller);
    }

    /**
     * STRICT attempts only — decrypts the per-submission AES-wrapped payload
     * with this module's own private key before funneling the plaintext through the
     * same answer path a STANDARD submission uses. The decrypted
     * plaintext is indistinguishable from a plain submission once past this point:
     * unchanged storage format.
     */
    @Transactional
    public AttemptTaskResponse submitEncryptedAnswer(UUID attemptPublicId, EncryptedSubmissionRequest request,
                                                      CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        answerRecorder.requireIntegrityLevel(attempt, "STRICT");
        String payload = submissionDecryptionService.decrypt(request, encryptionKeyProvider.getPrivateKey());
        return answerRecorder.processAnswer(attempt, request.pinnedItemPublicId(), payload, null, caller);
    }

    /** STRICT counterpart to {@link #saveAnswer}; the answer is decrypted and saved without advancing. */
    @Transactional
    public AttemptTaskResponse saveEncryptedAnswer(UUID attemptPublicId, EncryptedSubmissionRequest request,
                                                    CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        answerRecorder.requireIntegrityLevel(attempt, "STRICT");
        String payload = submissionDecryptionService.decrypt(request, encryptionKeyProvider.getPrivateKey());
        return answerRecorder.saveAnswerWithoutAdvance(attempt, request.pinnedItemPublicId(), payload, null, caller);
    }

    @Transactional
    public AttemptTaskResponse submitAttempt(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            // The request may have committed successfully while the student's
            // network response was lost. Replaying the terminal command must
            // return the same acknowledged terminal shape instead of turning a
            // successful submission into a retryable client error.
            if (attempt.getStatus() == AttemptStatus.SUBMITTED) {
                return attemptMapper.toCompletedResponse(attempt);
            }
            throw new AttemptAlreadyCompleteException();
        }
        access.completeAttempt(attempt);
        return attemptMapper.toCompletedResponse(attempt);
    }

    /**
     * Idempotent per {@code playRequestId} (client-generated UUID per user-initiated
     * play tap): a repeated request with the same key replays the prior outcome
     * instead of re-incrementing {@code playCount}. The pessimistic lock — taken on
     * {@code ExamAttempt} via {@link AttemptAccess#lockAttempt} — serializes concurrent plays for
     * the same attempt so two requests can never both observe the same pre-increment
     * {@code playCount}. The answer path's advance step and
     * {@code advanceUntilLiveOrComplete} (used by {@code getNextTask}/resume) take the
     * SAME lock before touching {@code currentOrderIndex}: {@code ExamAttempt} carries
     * {@code @Version}, so an unlocked writer racing this method's locked commit would
     * otherwise read a since-stale version and fail every such race with an uncaught
     * {@code ObjectOptimisticLockingFailureException}. Locking consistently everywhere
     * these fields are mutated means every writer only ever observes the version as
     * of its own lock acquisition, never a stale one.
     */
    @Transactional
    public AudioPlayResponse playAudio(UUID attemptPublicId, UUID pinnedItemPublicId, String playRequestId,
                                       CurrentUser caller) {
        ExamAttempt attempt = access.findOwned(attemptPublicId, caller);
        access.lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        attempt = access.lockAttempt(attempt);
        return audioPlayback.play(attempt, pinnedItemPublicId, playRequestId);
    }

    ExamAttempt findOwned(UUID publicId, UUID studentPublicId) {
        return access.findOwned(publicId, studentPublicId);
    }
}
