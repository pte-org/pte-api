package com.pte.practice.internal.service;

import com.pte.identity.PracticeIdentityService;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.PracticeSessionItem;
import com.pte.practice.internal.domain.PracticeSessionOperation;
import com.pte.practice.internal.domain.enums.PracticeSessionOperationType;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeCapabilityManifest;
import com.pte.practice.internal.dto.request.PracticeAnswerRequest;
import com.pte.practice.internal.dto.request.PracticeSaveAndExitRequest;
import com.pte.practice.internal.dto.request.PracticeSessionActionRequest;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeIdempotencyException;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.exception.PracticeSessionNotFoundException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.repository.PracticeSessionOperationRepository;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Owns the standalone product lifecycle while leaving task execution to the
 * later practice/attempt bridge. Every mutation is student-owned and guarded
 * by live entitlement plus an optimistic version.
 */
@Service
public class PracticeSessionService {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionPersistenceService sessionPersistenceService;
    private final PracticeSessionOperationRepository operationRepository;
    private final PracticeCatalogService catalogService;
    private final PracticeSessionItemService itemService;
    private final PracticeSessionResponseMapper responseMapper;
    private final PracticeEntitlementService entitlementService;
    private final PracticeIdentityService identityService;
    private final Clock clock;

    public PracticeSessionService(PracticeSessionRepository sessionRepository,
            PracticeSessionPersistenceService sessionPersistenceService,
            PracticeSessionOperationRepository operationRepository,
            PracticeCatalogService catalogService, PracticeEntitlementService entitlementService,
            PracticeSessionItemService itemService, PracticeSessionResponseMapper responseMapper,
            PracticeIdentityService identityService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionPersistenceService = sessionPersistenceService;
        this.operationRepository = operationRepository;
        this.catalogService = catalogService;
        this.itemService = itemService;
        this.responseMapper = responseMapper;
        this.entitlementService = entitlementService;
        this.identityService = identityService;
        this.clock = clock;
    }

    @Transactional
    public PracticeSessionResponse start(PracticeSessionStartRequest request, String rawIdempotencyKey,
            CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        String requestHash = startRequestHash(request);
        entitlementService.assertUnlocked(caller, request.organizationId());

        var existing = sessionRepository.findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(
                caller.userId(), idempotencyKey);
        if (existing.isPresent()) {
            assertSameRequest(existing.get(), requestHash);
            return responseMapper.toResponse(existing.get());
        }

        catalogService.requireStartable(request.productCode(), request.safeTaskTypeCodes(), request.capabilities());
        PracticeIdentityService.PracticeIdentityView identity = identityService
                .resolveForShellUser(caller.userId()).orElseThrow(PracticeNotEntitledException::new);
        requireActiveMembership(identity, request.organizationId());

        PracticeSession session = new PracticeSession();
        session.setStudentPublicId(caller.userId());
        session.setIdentityPublicId(identity.identityPublicId());
        session.setTenantId(request.organizationId());
        session.setProductCode(request.productCode());
        session.setTitle(PracticeConstants.PRACTICE_PRODUCT_TITLE);
        session.setTimeLimitSeconds(PracticeConstants.PRACTICE_TIME_LIMIT_SECONDS);
        session.setCatalogVersion(PracticeConstants.PRACTICE_CATALOG_VERSION);
        session.setSelectedTaskTypes(joinSorted(request.safeTaskTypeCodes()));
        session.setClientCapabilities(joinSorted(request.capabilities() == null
                ? Set.of() : request.capabilities().safeCapabilities()));
        session.setStartIdempotencyKey(idempotencyKey);
        session.setStartRequestHash(requestHash);
        session.setStatus(PracticeSessionStatus.OVERVIEW);
        try {
            return responseMapper.toResponse(sessionPersistenceService.saveStart(session));
        } catch (DataIntegrityViolationException ex) {
            return sessionRepository
                    .findByStudentPublicIdAndStartIdempotencyKeyAndDeletedFalse(caller.userId(), idempotencyKey)
                    .map(winner -> {
                        assertSameRequest(winner, requestHash);
                        return responseMapper.toResponse(winner);
                    })
                    .orElseThrow(() -> new PracticeSessionException(HttpStatus.SERVICE_UNAVAILABLE,
                            PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                            PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE));
        }
    }

    @Transactional
    public PracticeSessionResponse get(UUID publicId, CurrentUser caller) {
        PracticeSession session = findOwned(publicId, caller.userId());
        expireIfNeeded(session);
        return responseMapper.toResponse(session);
    }

    @Transactional
    public PracticeSessionResponse begin(UUID publicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = findOwnedForUpdate(publicId, caller.userId());
        String requestHash = actionRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        var existingOperation = operationRepository
                .findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                        session.getId(), PracticeSessionOperationType.BEGIN, idempotencyKey);
        if (existingOperation.isPresent()) {
            assertSameHash(existingOperation.get().getRequestHash(), requestHash);
            return responseMapper.toResponse(session);
        }
        requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.OVERVIEW) {
            throw notStartable();
        }
        catalogService.requireStartable(session.getProductCode(), splitCsv(session.getSelectedTaskTypes()),
                new PracticeCapabilityManifest(splitCsv(session.getClientCapabilities()), null));

        Instant now = clock.instant();
        itemService.createItemsIfNeeded(session);
        session.setStatus(PracticeSessionStatus.IN_PROGRESS);
        session.setStartedAt(now);
        session.setDeadlineAt(now.plusSeconds(session.getTimeLimitSeconds()));
        session.setLastActivityAt(now);
        saveOperation(session, PracticeSessionOperationType.BEGIN, idempotencyKey, requestHash);
        return saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse heartbeat(UUID publicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = findOwnedForUpdate(publicId, caller.userId());
        String requestHash = actionRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        var existingOperation = operationRepository
                .findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                        session.getId(), PracticeSessionOperationType.HEARTBEAT, idempotencyKey);
        if (existingOperation.isPresent()) {
            assertSameHash(existingOperation.get().getRequestHash(), requestHash);
            return responseMapper.toResponse(session);
        }
        if (session.getStatus() == PracticeSessionStatus.EXPIRED
                || isPastDeadline(session, clock.instant())) {
            expire(session);
            saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }
        requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw notStartable();
        }
        session.setLastActivityAt(clock.instant());
        saveOperation(session, PracticeSessionOperationType.HEARTBEAT, idempotencyKey, requestHash);
        return saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse answer(UUID publicId, UUID itemPublicId, PracticeAnswerRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = findOwnedForUpdate(publicId, caller.userId());
        String requestHash = answerRequestHash(itemPublicId, request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        var existingOperation = operationRepository
                .findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                        session.getId(), PracticeSessionOperationType.ANSWER, idempotencyKey);
        if (existingOperation.isPresent()) {
            assertSameHash(existingOperation.get().getRequestHash(), requestHash);
            return responseMapper.toResponse(session);
        }
        requireLiveInProgress(session, request.clientVersion());
        Instant now = clock.instant();
        PracticeSessionItem item = itemService.findForUpdate(session, itemPublicId);
        itemService.answer(item, request, now);
        itemService.completeIfFinished(session, now);
        session.setLastActivityAt(now);
        saveOperation(session, PracticeSessionOperationType.ANSWER, idempotencyKey, requestHash);
        return saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse skip(UUID publicId, UUID itemPublicId, PracticeSessionActionRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = findOwnedForUpdate(publicId, caller.userId());
        String requestHash = itemActionRequestHash(itemPublicId, request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        var existingOperation = operationRepository
                .findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                        session.getId(), PracticeSessionOperationType.SKIP, idempotencyKey);
        if (existingOperation.isPresent()) {
            assertSameHash(existingOperation.get().getRequestHash(), requestHash);
            return responseMapper.toResponse(session);
        }
        requireLiveInProgress(session, request.clientVersion());
        Instant now = clock.instant();
        PracticeSessionItem item = itemService.findForUpdate(session, itemPublicId);
        itemService.skip(item, now);
        itemService.completeIfFinished(session, now);
        session.setLastActivityAt(now);
        saveOperation(session, PracticeSessionOperationType.SKIP, idempotencyKey, requestHash);
        return saveAndMap(session);
    }

    @Transactional
    public PracticeSessionResponse saveAndExit(UUID publicId, PracticeSaveAndExitRequest request,
            String rawIdempotencyKey, CurrentUser caller) {
        String idempotencyKey = requireIdempotencyKey(rawIdempotencyKey);
        PracticeSession session = findOwnedForUpdate(publicId, caller.userId());
        String requestHash = saveAndExitRequestHash(request);
        entitlementService.assertUnlocked(caller, session.getTenantId());
        var existingOperation = operationRepository
                .findByPracticeSessionIdAndOperationTypeAndIdempotencyKeyAndDeletedFalse(
                        session.getId(), PracticeSessionOperationType.SAVE_AND_EXIT, idempotencyKey);
        if (existingOperation.isPresent()) {
            assertSameHash(existingOperation.get().getRequestHash(), requestHash);
            return responseMapper.toResponse(session);
        }
        requireVersion(session, request.clientVersion());
        if (session.getStatus() != PracticeSessionStatus.OVERVIEW
                && session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw notStartable();
        }
        if (session.getStatus() == PracticeSessionStatus.IN_PROGRESS
                && isPastDeadline(session, clock.instant())) {
            expire(session);
            saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }

        if (request.itemPublicId() == null && request.payload() != null && !request.payload().isBlank()) {
            throw invalidAnswerRequest();
        }
        if (request.itemPublicId() != null && request.payload() != null && !request.payload().isBlank()) {
            PracticeSessionItem item = itemService.findForUpdate(session, request.itemPublicId());
            itemService.saveDraft(item, request.payload(), request.confidence());
        }

        boolean hasProgress = itemService.hasProgress(session);
        Instant now = clock.instant();
        if (!hasProgress) {
            session.setStatus(PracticeSessionStatus.DISCARDED);
            session.setDiscardedAt(now);
        }
        session.setLastActivityAt(now);
        saveOperation(session, PracticeSessionOperationType.SAVE_AND_EXIT, idempotencyKey, requestHash);
        return saveAndMap(session);
    }

    private void requireLiveInProgress(PracticeSession session, long expectedVersion) {
        if (session.getStatus() == PracticeSessionStatus.EXPIRED
                || isPastDeadline(session, clock.instant())) {
            expire(session);
            saveAndMap(session);
            throw new PracticeSessionException(HttpStatus.GONE, PracticeConstants.PRACTICE_SESSION_EXPIRED,
                    PracticeConstants.PRACTICE_SESSION_EXPIRED_MESSAGE);
        }
        requireVersion(session, expectedVersion);
        if (session.getStatus() != PracticeSessionStatus.IN_PROGRESS) {
            throw notStartable();
        }
    }

    private void saveOperation(PracticeSession session, PracticeSessionOperationType operationType,
            String idempotencyKey, String requestHash) {
        PracticeSessionOperation operation = new PracticeSessionOperation();
        operation.setPracticeSessionId(session.getId());
        operation.setOperationType(operationType);
        operation.setIdempotencyKey(idempotencyKey);
        operation.setRequestHash(requestHash);
        operationRepository.save(operation);
    }

    private PracticeSessionResponse saveAndMap(PracticeSession session) {
        try {
            return responseMapper.toResponse(sessionRepository.saveAndFlush(session));
        } catch (OptimisticLockingFailureException ex) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION_MESSAGE);
        }
    }

    private PracticeSession findOwned(UUID publicId, UUID studentPublicId) {
        return sessionRepository.findByPublicIdAndStudentPublicIdAndDeletedFalse(publicId, studentPublicId)
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private PracticeSession findOwnedForUpdate(UUID publicId, UUID studentPublicId) {
        return sessionRepository.findWithLockByPublicIdAndStudentPublicId(publicId, studentPublicId)
                .orElseThrow(PracticeSessionNotFoundException::new);
    }

    private void requireActiveMembership(PracticeIdentityService.PracticeIdentityView identity,
            UUID organizationId) {
        boolean linked = identity.memberships().stream()
                .anyMatch(membership -> organizationId.equals(membership.tenantId())
                        && membership.isActiveStudent());
        if (!linked) {
            throw new PracticeNotEntitledException();
        }
    }

    private void requireVersion(PracticeSession session, long expectedVersion) {
        long actual = versionOf(session);
        if (actual != expectedVersion) {
            throw new PracticeSessionException(HttpStatus.CONFLICT,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION,
                    PracticeConstants.PRACTICE_STALE_SESSION_VERSION_MESSAGE);
        }
    }

    private void expireIfNeeded(PracticeSession session) {
        if (session.getStatus() == PracticeSessionStatus.IN_PROGRESS
                && isPastDeadline(session, clock.instant())) {
            expire(session);
            sessionRepository.saveAndFlush(session);
        }
    }

    private void expire(PracticeSession session) {
        session.setStatus(PracticeSessionStatus.EXPIRED);
    }

    private boolean isPastDeadline(PracticeSession session, Instant now) {
        return session.getDeadlineAt() != null && !now.isBefore(session.getDeadlineAt());
    }

    private PracticeSessionException notStartable() {
        return new PracticeSessionException(HttpStatus.CONFLICT,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE,
                PracticeConstants.PRACTICE_SESSION_NOT_STARTABLE_MESSAGE);
    }

    private PracticeSessionException invalidAnswerRequest() {
        return new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                PracticeConstants.PRACTICE_ANSWER_INVALID,
                PracticeConstants.PRACTICE_ANSWER_INVALID_MESSAGE);
    }

    private String requireIdempotencyKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new PracticeIdempotencyException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REQUIRED,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_REQUIRED_MESSAGE);
        }
        String key = rawKey.trim();
        if (key.length() > PracticeConstants.MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new PracticeIdempotencyException(HttpStatus.BAD_REQUEST,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_INVALID,
                    PracticeConstants.PRACTICE_IDEMPOTENCY_KEY_INVALID_MESSAGE);
        }
        return key;
    }

    private void assertSameRequest(PracticeSession session, String requestHash) {
        assertSameHash(session.getStartRequestHash(), requestHash);
    }

    private void assertSameHash(String storedHash, String requestHash) {
        if (!MessageDigest.isEqual(
                storedHash == null ? new byte[0] : storedHash.getBytes(StandardCharsets.UTF_8),
                requestHash.getBytes(StandardCharsets.UTF_8))) {
            throw PracticeIdempotencyException.reused();
        }
    }

    private String startRequestHash(PracticeSessionStartRequest request) {
        return sha256(String.join("\n",
                safe(request.productCode()),
                String.valueOf(request.organizationId()),
                joinSorted(request.safeTaskTypeCodes()),
                joinSorted(request.capabilities() == null ? Set.of() : request.capabilities().safeCapabilities())));
    }

    private String actionRequestHash(PracticeSessionActionRequest request) {
        return sha256(String.valueOf(request.clientVersion()));
    }

    private String itemActionRequestHash(UUID itemPublicId, PracticeSessionActionRequest request) {
        return sha256(String.join("\n", String.valueOf(itemPublicId),
                String.valueOf(request.clientVersion())));
    }

    private String answerRequestHash(UUID itemPublicId, PracticeAnswerRequest request) {
        return sha256(String.join("\n", String.valueOf(itemPublicId),
                String.valueOf(request.clientVersion()), safe(request.payload()),
                request.confidence() == null ? "" : request.confidence().name()));
    }

    private String saveAndExitRequestHash(PracticeSaveAndExitRequest request) {
        return sha256(String.join("\n", String.valueOf(request.clientVersion()),
                String.valueOf(request.itemPublicId()), safe(request.payload()),
                request.confidence() == null ? "" : request.confidence().name()));
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(PracticeConstants.PRACTICE_SHA256_UNAVAILABLE, ex);
        }
    }

    private String joinSorted(Collection<String> values) {
        return values == null ? "" : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .distinct().sorted().collect(java.util.stream.Collectors.joining(","));
    }

    private Set<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(csv.split(","))
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private long versionOf(PracticeSession session) {
        return session.getVersion() == null ? 0L : session.getVersion();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
