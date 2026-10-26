package com.pte.attempt.internal.service;

import com.pte.assessment.dto.response.SnapshotContentResponse;
import com.pte.attempt.domain.ExamAttempt;
import com.pte.attempt.domain.enums.AttemptStatus;
import com.pte.attempt.internal.config.EncryptionKeyProvider;
import com.pte.attempt.internal.dto.request.StartAttemptRequest;
import com.pte.attempt.internal.dto.response.AttemptTaskResponse;
import com.pte.attempt.internal.exception.AlreadyAttemptedException;
import com.pte.attempt.internal.exception.DeviceCheckRequiredException;
import com.pte.attempt.internal.exception.ExamCapabilityException;
import com.pte.attempt.internal.exception.RetryLimitReachedException;
import com.pte.attempt.internal.exception.content.ExamEmptyException;
import com.pte.attempt.internal.mapper.AttemptMapper;
import com.pte.attempt.internal.repository.ExamAttemptRepository;
import com.pte.attempt.internal.service.cache.ExamItemView;
import com.pte.attempt.internal.service.capability.CapabilityNegotiationService;
import com.pte.session.SessionService;
import com.pte.session.dto.response.EntitlementResponse;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Start-or-resume: the only lifecycle path that calls other modules
 * (session entitlement and assessment content) before the attempt is pinned.
 */
class AttemptStartFlow {

    private final ExamAttemptRepository attemptRepository;
    private final ExamContentService contentService;
    private final TimerService timerService;
    private final AttemptMapper attemptMapper;
    private final EncryptionKeyProvider encryptionKeyProvider;
    private final CapabilityNegotiationService capabilityNegotiationService;
    private final SessionService sessionService;
    private final AttemptAccess access;
    private final AttemptTaskProgression progression;
    private final Clock clock;

    AttemptStartFlow(ExamAttemptRepository attemptRepository, ExamContentService contentService,
            TimerService timerService, AttemptMapper attemptMapper, EncryptionKeyProvider encryptionKeyProvider,
            CapabilityNegotiationService capabilityNegotiationService, SessionService sessionService,
            AttemptAccess access, AttemptTaskProgression progression, Clock clock) {
        this.attemptRepository = attemptRepository;
        this.contentService = contentService;
        this.timerService = timerService;
        this.attemptMapper = attemptMapper;
        this.encryptionKeyProvider = encryptionKeyProvider;
        this.capabilityNegotiationService = capabilityNegotiationService;
        this.sessionService = sessionService;
        this.access = access;
        this.progression = progression;
        this.clock = clock;
    }

    AttemptTaskResponse startAttempt(StartAttemptRequest request, CurrentUser caller) {
        sessionService.lockOpenForAttemptOperation(request.sessionPublicId(), caller.tenantId());
        UUID studentPublicId = caller.userId();
        var latest = attemptRepository.findFirstBySessionPublicIdAndStudentPublicIdOrderByAttemptNumberDesc(
                request.sessionPublicId(), studentPublicId);
        if (latest.isPresent() && (latest.get().getStatus() == AttemptStatus.CREATED
                || latest.get().getStatus() == AttemptStatus.IN_PROGRESS)) {
            access.requireTenantOwnership(latest.get(), caller);
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

    private AttemptTaskResponse resumeOrReject(ExamAttempt existing, CurrentUser caller) {
        if (existing.getStatus() != AttemptStatus.CREATED && existing.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new AlreadyAttemptedException();
        }
        return progression.advanceUntilLiveOrComplete(existing, caller);
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
        attempt.setDeviceCheckPassedAt(clock.instant());
        attempt.begin(clock.instant());
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
}
