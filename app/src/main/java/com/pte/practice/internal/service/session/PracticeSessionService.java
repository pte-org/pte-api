package com.pte.practice.internal.service.session;

import com.pte.identity.PracticeIdentityService;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeResponse;
import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeSaveAndExitRequest;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeItemResultResponse;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.practice.internal.service.PracticeResponseService;
import com.pte.shared.idempotency.IdempotencyKeyService;
import com.pte.shared.security.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.actionRequestHash;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.answerRequestHash;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.itemActionRequestHash;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.requireIdempotencyKey;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.saveAndExitRequestHash;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.splitCsv;

/**
 * Owns the standalone product lifecycle while leaving task execution to the
 * later practice/attempt bridge. Every mutation is student-owned and guarded
 * by live entitlement plus an optimistic version.
 */
@Service
public class PracticeSessionService {

    private final PracticeCatalogService catalogService;
    private final PracticeResponseService itemService;
    private final PracticeSessionResponseMapper responseMapper;
    private final PracticeEntitlementService entitlementService;
    private final Clock clock;
    private final PracticeSessionStarter sessionStarter;
    private final PracticeSessionStateGuard stateGuard;
    private final PracticeSessionOperationLedger operationLedger;

    public PracticeSessionService(PracticeSessionRepository sessionRepository,
            PracticeSessionPersistenceService sessionPersistenceService,
            IdempotencyKeyService idempotencyKeyService,
            PracticeCatalogService catalogService, PracticeEntitlementService entitlementService,
            PracticeResponseService itemService, PracticeSessionResponseMapper responseMapper,
            PracticeIdentityService identityService, Clock clock) {
        this.catalogService = catalogService;
        this.itemService = itemService;
        this.responseMapper = responseMapper;
        this.entitlementService = entitlementService;
        this.clock = clock;
        this.sessionStarter = new PracticeSessionStarter(sessionRepository, sessionPersistenceService,
                catalogService, responseMapper, entitlementService, identityService);
        this.stateGuard = new PracticeSessionStateGuard(sessionRepository, responseMapper, clock);
        this.operationLedger = new PracticeSessionOperationLedger(idempotencyKeyService, clock);
    }

    @Transactional
    public PracticeSessionResponse start(PracticeSessionStartRequest request, String rawIdempotencyKey,
            CurrentUser caller) {
        return sessionStarter.start(request, rawIdempotencyKey, caller);
    }

    @Transactional
    public PracticeSessionResponse get(UUID publicId, CurrentUser caller) {
        PracticeSession session = stateGuard.findOwned(publicId, caller.userId());
        stateGuard.expireIfNeeded(session);
        itemService.recoverScoring(session, clock.instant());
        return responseMapper.toResponse(session);
    }

    @Transactional
    public PracticeSessionResponse begin(UUID publicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        String requestHash = actionRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (operationLedger.alreadyApplied(session, PracticeSessionOperationType.BEGIN, idempotencyKey, requestHash)) {
            return responseMapper.toResponse(session);
        }
        stateGuard.requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.OVERVIEW) {
            throw stateGuard.notStartable();
        }
        catalogService.requireStartable(session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));

        Instant now = clock.instant();
        itemService.createItemsIfNeeded(session);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setStartedAt(now);
        session.setDeadlineAt(now.plusSeconds(session.getTimeLimitSeconds()));
        session.setLastActivityAt(now);
        operationLedger.recordOperation(session, PracticeSessionOperationType.BEGIN, idempotencyKey);
        return stateGuard.saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse heartbeat(UUID publicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        String requestHash = actionRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (operationLedger.alreadyApplied(session, PracticeSessionOperationType.HEARTBEAT, idempotencyKey,
                requestHash)) {
            return responseMapper.toResponse(session);
        }
        if (session.getStatus() == PracticeSessionStatus.EXPIRED
                || stateGuard.isPastDeadline(session, clock.instant())) {
            stateGuard.expire(session);
            stateGuard.saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }
        stateGuard.requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw stateGuard.notStartable();
        }
        session.setLastActivityAt(clock.instant());
        operationLedger.recordOperation(session, PracticeSessionOperationType.HEARTBEAT, idempotencyKey);
        return stateGuard.saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse answer(UUID publicId, UUID itemPublicId, PracticeAnswerRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        String requestHash = answerRequestHash(itemPublicId, request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (operationLedger.alreadyApplied(session, PracticeSessionOperationType.ANSWER, idempotencyKey, requestHash)) {
            PracticeResponse answered = itemService.findForUpdate(session, itemPublicId);
            itemService.recoverScoring(session, clock.instant());
            return responseMapper.toResponse(session).withItemResult(itemService.resultOf(answered));
        }
        stateGuard.requireLiveInProgress(session, request.clientVersion());
        Instant now = clock.instant();
        PracticeResponse item = itemService.findForUpdate(session, itemPublicId);
        itemService.answer(session, item, request, caller, now);
        itemService.completeIfFinished(session, now);
        session.setLastActivityAt(now);
        operationLedger.recordOperation(session, PracticeSessionOperationType.ANSWER, idempotencyKey);
        return stateGuard.saveAndMap(session).withItemResult(itemService.resultOf(item));
    }

    /**
     * Poll for one item's score. The item row is locked so the stale-job repair cannot race the worker's write.
     * Ownership is checked through the session, so another student's or tenant's item is not found.
     */
    @Transactional
    public PracticeItemResultResponse itemResult(UUID publicId, UUID itemPublicId, CurrentUser caller) {
        PracticeSession session = stateGuard.findOwned(publicId, caller.userId());
        PracticeResponse item = itemService.findForUpdate(session, itemPublicId);
        itemService.recoverScoring(session, item, clock.instant());
        return itemService.resultOf(item);
    }

    /** Re-submits a FAILED AI item (at most the configured retries); a SCORED item is final. */
    @Transactional
    public PracticeItemResultResponse retryScoring(UUID publicId, UUID itemPublicId, CurrentUser caller) {
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        entitlementService.assertUnlocked(caller, session.getTenantId());
        PracticeResponse item = itemService.findForUpdate(session, itemPublicId);
        return itemService.retryScoring(session, item, clock.instant());
    }

    @Transactional
    public PracticeSessionResponse skip(UUID publicId, UUID itemPublicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        String requestHash = itemActionRequestHash(itemPublicId, request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (operationLedger.alreadyApplied(session, PracticeSessionOperationType.SKIP, idempotencyKey, requestHash)) {
            return responseMapper.toResponse(session);
        }
        stateGuard.requireLiveInProgress(session, request.clientVersion());
        Instant now = clock.instant();
        PracticeResponse item = itemService.findForUpdate(session, itemPublicId);
        itemService.skip(item, now);
        itemService.completeIfFinished(session, now);
        session.setLastActivityAt(now);
        operationLedger.recordOperation(session, PracticeSessionOperationType.SKIP, idempotencyKey);
        return stateGuard.saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse saveAndExit(UUID publicId, PracticeSaveAndExitRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = stateGuard.findOwnedForUpdate(publicId, caller.userId());
        String requestHash = saveAndExitRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        if (operationLedger.alreadyApplied(session, PracticeSessionOperationType.SAVE_AND_EXIT, idempotencyKey,
                requestHash)) {
            return responseMapper.toResponse(session);
        }
        stateGuard.requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.OVERVIEW
                && session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw stateGuard.notStartable();
        }
        if (session.getStatus() == PracticeSessionStatus.IN_PROGRESS
                && stateGuard.isPastDeadline(session, clock.instant())) {
            stateGuard.expire(session);
            stateGuard.saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }

        if (request.itemPublicId() == null && request.payload() != null && !request.payload().isBlank()) {
            throw stateGuard.invalidAnswerRequest();
        }
        if (request.itemPublicId() != null && request.payload() != null && !request.payload().isBlank()) {
            PracticeResponse item = itemService.findForUpdate(session, request.itemPublicId());
            itemService.saveDraft(session, item, request.payload(), request.confidence(), caller);
        }

        boolean hasProgress = itemService.hasProgress(session);
        Instant now = clock.instant();
        if (!hasProgress) {
            session.setStatus(PracticeSessionStatus.DISCARDED);
            session.setDiscardedAt(now);
        }
        session.setLastActivityAt(now);
        operationLedger.recordOperation(session, PracticeSessionOperationType.SAVE_AND_EXIT, idempotencyKey);
        return stateGuard.saveAndMap(session);
    }
}
