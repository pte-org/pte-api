package com.pte.attempt.internal.service;

import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.constant.AttemptConstants;
import com.pte.attempt.internal.exception.AlreadyAttemptedException;
import com.pte.attempt.internal.exception.AnswerIntegrityLevelMismatchException;
import com.pte.attempt.internal.exception.AttemptAlreadyCompleteException;
import com.pte.attempt.internal.exception.AttemptNotFoundException;
import com.pte.attempt.internal.exception.AudioUrlExpiredException;
import com.pte.attempt.internal.exception.DeviceCheckRequiredException;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.NotCurrentTaskException;
import com.pte.attempt.internal.exception.ExamEmptyException;
import com.pte.attempt.internal.exception.ReplayLimitExceededException;
import com.pte.attempt.internal.exception.RetryLimitReachedException;
import com.pte.attempt.internal.dto.request.EncryptedSubmissionRequest;
import com.pte.attempt.internal.dto.request.AttemptPreflightRequest;
import com.pte.attempt.internal.dto.request.ClientCapabilityManifest;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.request.NavigateTaskRequest;
import com.pte.attempt.internal.dto.request.SubmitAnswerRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.dto.response.AudioPlayResponse;
import com.pte.attempt.internal.dto.response.AttemptPreflightResponse;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.policy.ManualNavigationPolicy;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.shared.security.CurrentUser;
import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The attempt state machine: CREATED → IN_PROGRESS → SUBMITTED (SCORING/
 * SCORED/PUBLISHED are driven by application events from later phases, out
 * of scope here). Once {@link #startAttempt} returns, nothing in this class
 * calls another module again — every method after that operates purely on
 * this module's own data and the cached exam content (Phase 07's central invariant).
 */
@Service
public class AttemptLifecycleService {

    private final ExamAttemptRepository attemptRepository;
    private final ExamContentService contentService;
    private final TimerService timerService;
    private final AnswerSubmitService answerSubmitService;
    private final AttemptMapper attemptMapper;
    private final EncryptionKeyProvider encryptionKeyProvider;
    private final SubmissionDecryptionService submissionDecryptionService;
    private final HeartbeatService heartbeatService;
    private final CapabilityNegotiationService capabilityNegotiationService;
    private final SessionService sessionService;

    public AttemptLifecycleService(ExamAttemptRepository attemptRepository, ExamContentService contentService,
                          TimerService timerService, AnswerSubmitService answerSubmitService,
                          AttemptMapper attemptMapper, EncryptionKeyProvider encryptionKeyProvider,
                          SubmissionDecryptionService submissionDecryptionService, HeartbeatService heartbeatService,
                          CapabilityNegotiationService capabilityNegotiationService,
                          SessionService sessionService) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
        this.timerService = timerService;
        this.answerSubmitService = answerSubmitService;
        this.attemptMapper = attemptMapper;
        this.encryptionKeyProvider = encryptionKeyProvider;
        this.submissionDecryptionService = submissionDecryptionService;
        this.heartbeatService = heartbeatService;
        this.capabilityNegotiationService = capabilityNegotiationService;
        this.sessionService = sessionService;
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
        requireTenantOwnership(attempt, caller);
        lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        heartbeatService.recordHeartbeat(attempt);
    }

    @Transactional
    public AttemptTaskResponse startAttempt(StartAttemptRequest request, CurrentUser caller) {
        sessionService.lockOpenForAttemptOperation(request.sessionPublicId(), caller.tenantId());
        UUID studentPublicId = caller.userId();
        var latest = attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                request.sessionPublicId(), studentPublicId);
        if (latest.isPresent() && (latest.get().getStatus() == AttemptStatus.CREATED
                || latest.get().getStatus() == AttemptStatus.IN_PROGRESS)) {
            requireTenantOwnership(latest.get(), caller);
            capabilityNegotiationService.authorizeExisting(latest.get(), request.capabilityManifest(), caller);
            return resumeOrReject(latest.get(), caller);
        }

        sessionService.assertTenantActiveForNewAttempt(caller.tenantId());
        int maxRetries = configuredRetries(request.sessionPublicId(), caller.tenantId());
        long submittedAttempts = attemptRepository.countBySessionPublicIdAndStudentPublicIdAndStatus(
                request.sessionPublicId(), studentPublicId, AttemptStatus.SUBMITTED);
        if (submittedAttempts >= 1L + maxRetries) {
            throw new RetryLimitReachedException();
        }

        String capabilityFingerprint = capabilityNegotiationService.authorizeStart(request.sessionPublicId(),
                studentPublicId, request.capabilityManifest(), caller);
        return createAndStart(request.sessionPublicId(), studentPublicId, caller.tenantId(),
                request.deviceCheckConfirmed(), capabilityFingerprint, caller, (int) submittedAttempts + 1,
                maxRetries);
    }

    private int configuredRetries(UUID sessionPublicId, UUID tenantId) {
        return sessionService.getAttemptRetryPolicy(sessionPublicId, tenantId).maxRetriesPerStudent();
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
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return List.of(attemptMapper.toCompletedResponse(attempt));
        }
        assertStoredCapabilities(attempt, caller);
        List<ExamItemView> allItems = contentService.items(attempt);
        String encryptionPublicKey = "STRICT".equals(attempt.getAnswerIntegrityLevel())
                ? encryptionKeyProvider.getPublicKeyBase64()
                : null;
        return allItems.stream()
                .map(item -> {
                    int effectivePrep = timerService.resolveEffectivePrepSeconds(item);
                    int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, item, allItems);
                    return attemptMapper.toTaskResponse(attempt, item, effectivePrep, effectiveResponse,
                            allItems, encryptionPublicKey);
                })
                .toList();
    }

    @Transactional
    public AttemptTaskResponse getNextTask(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attemptMapper.toCompletedResponse(attempt);
        }
        return advanceUntilLiveOrComplete(attempt, caller);
    }

    /**
     * Moves the live task pointer by one item without submitting an answer.
     * Both adjacent sections are checked server-side;
     * the client-provided item id is only a stale-screen guard.
     */
    @Transactional
    public AttemptTaskResponse navigateTask(UUID attemptPublicId, NavigateTaskRequest request, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            return attemptMapper.toCompletedResponse(attempt);
        }
        attempt = lockAttempt(attempt);

        ExamItemView sourceItem = contentService.item(attempt, request.fromPinnedItemPublicId())
                .orElseThrow(NotCurrentTaskException::new);
        int sourceIndex = sourceItem.orderIndex();
        int targetIndex = sourceIndex + (request.direction() == NavigateTaskRequest.Direction.NEXT ? 1 : -1);
        long totalTasks = contentService.items(attempt).size();
        int currentIndex = attempt.getCurrentOrderIndex();

        // The answer-submit path may already have advanced one item before an
        // older client retries navigation. Reject anything further out of date.
        if (currentIndex != sourceIndex && currentIndex != sourceIndex + 1) {
            throw new NotCurrentTaskException();
        }

        if (targetIndex < 0) {
            return advanceUntilLiveOrComplete(attempt, caller);
        }

        if (targetIndex >= totalTasks) {
            if (!ManualNavigationPolicy.allowsSection(sourceItem.section())) {
                throw new NotCurrentTaskException();
            }
            answerSubmitService.submitIfAbsent(attempt, sourceItem, null);
            completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }

        ExamItemView targetItem = contentService.itemAt(attempt, targetIndex);
        if (!ManualNavigationPolicy.allowsSection(sourceItem.section())
                || !ManualNavigationPolicy.allowsSection(targetItem.section())) {
            throw new NotCurrentTaskException();
        }

        // A skipped task still needs a blank answer for scoring; an existing
        // answer from an earlier visit is left untouched.
        answerSubmitService.submitIfAbsent(attempt, sourceItem, null);
        if (currentIndex != targetIndex) {
            timerService.startTask(attempt, targetItem, contentService.items(attempt));
            attemptRepository.save(attempt);
        }
        return advanceUntilLiveOrComplete(attempt, caller);
    }

    /** STANDARD attempts only — a STRICT attempt must use {@link #submitEncryptedAnswer}. */
    @Transactional
    public AttemptTaskResponse submitAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        requireIntegrityLevel(attempt, "STANDARD");
        return processAnswer(attempt, request.pinnedItemPublicId(), request.payload(), request.confidence(), caller);
    }

    /** Persists an answer draft while leaving the current task pointer unchanged. */
    @Transactional
    public AttemptTaskResponse saveAnswer(UUID attemptPublicId, SubmitAnswerRequest request, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        requireIntegrityLevel(attempt, "STANDARD");
        return saveAnswerWithoutAdvance(attempt, request.pinnedItemPublicId(), request.payload(), request.confidence(), caller);
    }

    /**
     * STRICT attempts only — decrypts the per-submission AES-wrapped payload
     * with this module's own private key before funneling the plaintext through the
     * same {@link #processAnswer} path a STANDARD submission uses. The decrypted
     * plaintext is indistinguishable from a plain submission once past this point:
     * unchanged storage format.
     */
    @Transactional
    public AttemptTaskResponse submitEncryptedAnswer(UUID attemptPublicId, EncryptedSubmissionRequest request,
                                                      CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        requireIntegrityLevel(attempt, "STRICT");
        String payload = submissionDecryptionService.decrypt(request, encryptionKeyProvider.getPrivateKey());
        return processAnswer(attempt, request.pinnedItemPublicId(), payload, null, caller);
    }

    /** STRICT counterpart to {@link #saveAnswer}; the answer is decrypted and saved without advancing. */
    @Transactional
    public AttemptTaskResponse saveEncryptedAnswer(UUID attemptPublicId, EncryptedSubmissionRequest request,
                                                    CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        requireIntegrityLevel(attempt, "STRICT");
        String payload = submissionDecryptionService.decrypt(request, encryptionKeyProvider.getPrivateKey());
        return saveAnswerWithoutAdvance(attempt, request.pinnedItemPublicId(), payload, null, caller);
    }

    /** Request shape (plain vs. encrypted) is server-decided by the attempt level, never client-chosen. */
    private void requireIntegrityLevel(ExamAttempt attempt, String expectedLevel) {
        if (!expectedLevel.equals(attempt.getAnswerIntegrityLevel())) {
            throw new AnswerIntegrityLevelMismatchException();
        }
    }

    private AttemptTaskResponse processAnswer(ExamAttempt attempt, UUID pinnedItemPublicId, String payload,
                                               com.pte.attempt.ResponseConfidence confidence,
                                               CurrentUser caller) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        // Same lock as playAudio: currentOrderIndex/playCount now live on the
        // same @Version-guarded ExamAttempt row playAudio locks. Without also
        // locking here, a concurrent playAudio commit between this method's
        // unlocked read and its eventual flush bumps `version` out from under
        // this transaction, and Hibernate's dirty-check UPDATE (WHERE id=? AND
        // version=<stale>) matches zero rows -> ObjectOptimisticLockingFailureException,
        // uncaught, surfaces as a bare 500. Locking here too means this
        // transaction only ever reads the version as of its own lock
        // acquisition, never a stale one.
        attempt = lockAttempt(attempt);
        ExamItemView currentItem = contentService.itemAt(attempt, attempt.getCurrentOrderIndex());
        if (!currentItem.publicId().equals(pinnedItemPublicId)) {
            throw new NotCurrentTaskException();
        }

        persistAnswer(attempt, currentItem, payload, confidence);
        return advanceAfterCurrent(attempt, caller);
    }

    private AttemptTaskResponse saveAnswerWithoutAdvance(ExamAttempt attempt, UUID pinnedItemPublicId,
                                                          String payload,
                                                          com.pte.attempt.ResponseConfidence confidence,
                                                          CurrentUser caller) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        attempt = lockAttempt(attempt);
        ExamItemView answerItem = contentService.item(attempt, pinnedItemPublicId)
                .orElseThrow(NotCurrentTaskException::new);
        // Saving an answer does not move the task pointer. Allow background
        // sync for every section; mode restrictions apply only to navigation.
        persistAnswer(attempt, answerItem, payload, confidence);
        return advanceUntilLiveOrComplete(attempt, caller);
    }

    private void persistAnswer(ExamAttempt attempt, ExamItemView item, String payload,
            com.pte.attempt.ResponseConfidence confidence) {
        if (confidence == null) {
            // Preserve the established official call shape and behavior for
            // clients that do not send the additive practice field.
            answerSubmitService.submit(attempt, item, payload);
        } else {
            answerSubmitService.submit(attempt, item, payload, confidence);
        }
    }

    @Transactional
    public AttemptTaskResponse submitAttempt(UUID attemptPublicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
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
        completeAttempt(attempt);
        return attemptMapper.toCompletedResponse(attempt);
    }

    /**
     * Idempotent per {@code playRequestId} (client-generated UUID per user-initiated
     * play tap): a repeated request with the same key replays the prior outcome
     * instead of re-incrementing {@code playCount}. The pessimistic lock — taken on
     * {@code ExamAttempt} via {@link #lockAttempt} — serializes concurrent plays for
     * the same attempt so two requests can never both observe the same pre-increment
     * {@code playCount}. {@code processAnswer}'s advance step and
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
        ExamAttempt attempt = findOwned(attemptPublicId, caller);
        lockOpenSession(attempt);
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AttemptAlreadyCompleteException();
        }
        attempt = lockAttempt(attempt);
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
        return authenticatedCloudinaryUrl && Instant.now().isAfter(expiresAt);
    }

    private AttemptTaskResponse resumeOrReject(ExamAttempt existing, CurrentUser caller) {
        if (existing.getStatus() != AttemptStatus.CREATED && existing.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AlreadyAttemptedException();
        }
        return advanceUntilLiveOrComplete(existing, caller);
    }

    private AttemptTaskResponse createAndStart(UUID sessionPublicId, UUID studentPublicId, UUID tenantId,
                                             boolean deviceCheckConfirmed, String capabilityFingerprint,
                                             CurrentUser caller, int attemptNumber, int maxRetriesPerStudent) {
        // Throws session's own 403 unmodified if the student is not enrolled or the session is not open.
        EntitlementResponse entitlement = sessionService.checkEntitlement(sessionPublicId, studentPublicId);
        SnapshotContentResponse content = contentService.header(entitlement.snapshotPublicId());

        ExamAttempt attempt = new ExamAttempt();
        attempt.setSessionPublicId(sessionPublicId);
        attempt.setStudentPublicId(studentPublicId);
        attempt.setTenantId(tenantId);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setMaxRetriesPerStudent(maxRetriesPerStudent);
        attempt.setCapabilityFingerprint(capabilityFingerprint);
        copyExamTerms(attempt, entitlement, content);
        try {
            attempt = attemptRepository.save(attempt);
        } catch (DataIntegrityViolationException ex) {
            throw new AlreadyAttemptedException();
        }

        List<ExamItemView> items;
        try {
            items = contentService.loadForStart(attempt.getPublicId(), content, entitlement.tenantId(),
                    entitlement.opensAt(), entitlement.closesAt());
        } catch (ExamCapabilityException ex) {
            capabilityNegotiationService.recordBlockedDelivery(caller, attempt.getPublicId(), ex.getCode());
            throw ex;
        }
        if (items.isEmpty()) {
            throw new ExamEmptyException();
        }
        capabilityNegotiationService.verifyForStart(items, attempt.getExamVersionPublicId(), capabilityFingerprint,
                caller);
        if (attempt.isDeviceCheckRequired() && !deviceCheckConfirmed) {
            throw new DeviceCheckRequiredException();
        }
        attempt.setDeviceCheckPassedAt(Instant.now());
        attempt.begin();
        long totalExamSeconds = items.stream()
                .mapToLong(item -> (long) item.prepSeconds() + item.responseSeconds())
                .sum();
        attempt.setExamEndTime(attempt.getStartedAt().plusSeconds(totalExamSeconds));
        String encryptionPublicKey = "STRICT".equals(attempt.getAnswerIntegrityLevel())
                ? encryptionKeyProvider.getPublicKeyBase64()
                : null;
        attempt = attemptRepository.save(attempt);

        ExamItemView first = items.get(0);
        timerService.startTask(attempt, first, items);
        int effectivePrep = timerService.resolveEffectivePrepSeconds(first);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, first, items);
        return attemptMapper.toTaskResponse(attempt, first, effectivePrep, effectiveResponse, items,
                encryptionPublicKey);
    }

    /** The attempt keeps the rules it started under, so a later session edit can never change a running exam. */
    private void copyExamTerms(ExamAttempt attempt, EntitlementResponse entitlement, SnapshotContentResponse content) {
        attempt.setExamVersionPublicId(content.publicId());
        attempt.setExamDefinitionPublicId(content.examDefinitionPublicId());
        attempt.setExamDefinitionVersion(content.examDefinitionVersion());
        attempt.setReplayPolicyType(entitlement.policy().replayPolicyType());
        attempt.setReplayPolicyLimit(entitlement.policy().replayPolicyLimit());
        attempt.setDeviceCheckRequired(Boolean.TRUE.equals(entitlement.policy().deviceCheckRequired()));
        attempt.setProctorRequired(Boolean.TRUE.equals(entitlement.policy().proctorRequired()));
        attempt.setAnswerIntegrityLevel(entitlement.policy().answerIntegrityLevel());
        attempt.setLockdownMode(entitlement.policy().lockdownMode());
        attempt.setExamMode(entitlement.examMode() == null ? "OFFICIAL_EXAM" : entitlement.examMode());
    }

    /**
     * No expiry sweep — {@code currentOrderIndex} is already correct as
     * maintained by whichever call last advanced it ({@link #createAndPin}/
     * {@link #advanceAfterCurrent}), so this is a pure read, needing no lock
     * (nothing here mutates {@code attempt}). Any drift now surfaces directly
     * as a data-integrity bug at its source, rather than being silently
     * patched over here.
     */
    private AttemptTaskResponse advanceUntilLiveOrComplete(ExamAttempt attempt, CurrentUser caller) {
        assertStoredCapabilities(attempt, caller);
        List<ExamItemView> allItems = contentService.items(attempt);
        ExamItemView currentView = contentService.itemAt(attempt, attempt.getCurrentOrderIndex());
        int effectivePrep = timerService.resolveEffectivePrepSeconds(currentView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, currentView, allItems);
        return attemptMapper.toTaskResponse(attempt, currentView, effectivePrep, effectiveResponse, allItems);
    }

    private AttemptTaskResponse advanceAfterCurrent(ExamAttempt attempt, CurrentUser caller) {
        List<ExamItemView> allItems = contentService.items(attempt);
        int nextIndex = attempt.getCurrentOrderIndex() + 1;
        if (nextIndex >= allItems.size()) {
            completeAttempt(attempt);
            return attemptMapper.toCompletedResponse(attempt);
        }
        assertStoredCapabilities(attempt, caller);
        ExamItemView nextView = contentService.itemAt(attempt, nextIndex);
        timerService.startTask(attempt, nextView, allItems);
        int effectivePrep = timerService.resolveEffectivePrepSeconds(nextView);
        int effectiveResponse = timerService.resolveEffectiveResponseSeconds(attempt, nextView, allItems);
        return attemptMapper.toTaskResponse(attempt, nextView, effectivePrep, effectiveResponse, allItems);
    }

    private void assertStoredCapabilities(ExamAttempt attempt, CurrentUser caller) {
        capabilityNegotiationService.assertStoredCapabilities(attempt, caller);
    }

    /**
     * Locked here (not just at each caller) so every path to completing an
     * attempt — including submitAttempt, which had no other lock on this
     * @Version-guarded row — is covered by a single choke point. Callers that
     * already locked earlier in the same transaction just re-acquire their
     * own already-held lock — a cheap, harmless no-op, not a double-lock
     * hazard (same transaction, same row).
     *
     * <p>{@link ExamAttempt#submit()} registers an {@code AttemptSubmittedEvent}, which
     * the repository publishes on save; scoring ingests the answers after commit.
     */
    private void completeAttempt(ExamAttempt attempt) {
        lockAttempt(attempt);
        attempt.submit();
        attemptRepository.save(attempt);
    }

    ExamAttempt findOwned(UUID publicId, UUID studentPublicId) {
        return attemptRepository.findByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(AttemptNotFoundException::new);
    }

    private ExamAttempt findOwned(UUID publicId, CurrentUser caller) {
        ExamAttempt attempt = findOwned(publicId, caller.userId());
        requireTenantOwnership(attempt, caller);
        return attempt;
    }

    private void lockOpenSession(ExamAttempt attempt) {
        sessionService.lockOpenForAttemptOperation(attempt.getSessionPublicId(), attempt.getTenantId());
    }

    private void requireTenantOwnership(ExamAttempt attempt, CurrentUser caller) {
        if (caller.tenantId() == null || attempt.getTenantId() == null
                || !caller.tenantId().equals(attempt.getTenantId())) {
            throw new AttemptNotFoundException();
        }
    }

    /**
     * Re-fetches {@code attempt} under a pessimistic write lock, by id, within the
     * same persistence context — returns the SAME managed instance (JPA identity map),
     * so already-loaded state stays populated.
     * Every mutator of the relocated {@code currentOrderIndex}/{@code playCount}/
     * {@code lastPlayRequestId}/{@code lastPlayAllowed} fields must call this first.
     */
    private ExamAttempt lockAttempt(ExamAttempt attempt) {
        Long attemptId = attempt.getId();
        return attemptRepository.findWithLockById(attemptId)
                .orElseThrow(() -> new IllegalStateException(
                        String.format(AttemptConstants.ATTEMPT_DISAPPEARED_MID_TRANSACTION, attemptId)));
    }
}
