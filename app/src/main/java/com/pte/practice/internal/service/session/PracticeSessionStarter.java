package com.pte.practice.internal.service.session;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionKind;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.domain.enums.PracticeTimingMode;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.practice.internal.service.PracticeMockTestService;
import com.pte.practice.internal.service.PracticeTimingService;
import com.pte.shared.security.CurrentUser;
import java.time.Clock;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.assertSameRequest;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.joinSorted;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.requireIdempotencyKey;
import static com.pte.practice.internal.service.session.PracticeSessionRequestHashes.startRequestHash;

/** Creates a session in OVERVIEW for an entitled student, replaying the original for a repeated start key. */
class PracticeSessionStarter {

    private final PracticeSessionRepository sessionRepository;
    private final PracticeSessionPersistenceService sessionPersistenceService;
    private final PracticeCatalogService catalogService;
    private final PracticeSessionResponseMapper responseMapper;
    private final PracticeEntitlementService entitlementService;
    private final PracticeTimingService timingService;
    private final Clock clock;
    private PracticeMockTestService mockTestService;

    void setMockTestService(PracticeMockTestService service) { this.mockTestService = service; }

    PracticeSessionStarter(PracticeSessionRepository sessionRepository,
            PracticeSessionPersistenceService sessionPersistenceService, PracticeCatalogService catalogService,
            PracticeSessionResponseMapper responseMapper, PracticeEntitlementService entitlementService,
            PracticeTimingService timingService, Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionPersistenceService = sessionPersistenceService;
        this.catalogService = catalogService;
        this.responseMapper = responseMapper;
        this.entitlementService = entitlementService;
        this.timingService = timingService;
        this.clock = clock;
    }

    PracticeSessionResponse start(PracticeSessionStartRequest request, String rawIdempotencyKey,
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

        if (request.hasMockTest()) {
            return startMockTest(request, idempotencyKey, requestHash, caller);
        }
        Set<String> taskCodes = resolveTaskCodes(request);
        catalogService.requireStartable(request.productCode(), taskCodes, request.capabilities());
        if (request.effectiveTimingMode() == PracticeTimingMode.TIMED) {
            requireTimedAvailable(taskCodes);
        }

        PracticeSession session = new PracticeSession();
        session.setStudentPublicId(caller.userId());
        // assertUnlocked already proved the requested organization is the caller's own tenant.
        session.setTenantId(caller.tenantId());
        session.setProductCode(request.productCode());
        session.setTitle(PracticeConstants.PRACTICE_PRODUCT_TITLE);
        session.setTimeLimitSeconds(PracticeConstants.PRACTICE_NO_SESSION_TIME_LIMIT_SECONDS);
        session.setTimingMode(request.effectiveTimingMode());
        session.setQuestionCount(request.effectiveQuestionCount());
        session.setCatalogVersion(PracticeConstants.PRACTICE_CATALOG_VERSION);
        session.setSelectedTaskTypes(joinSorted(taskCodes));
        session.setKind(request.hasSkill() ? PracticeSessionKind.SKILL : PracticeSessionKind.TASK);
        session.setSkillCode(request.hasSkill() ? request.skillCode() : null);
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

    private PracticeSessionResponse startMockTest(PracticeSessionStartRequest request, String idempotencyKey,
            String requestHash, CurrentUser caller) {
        if (request.hasSkill() || !request.safeTaskTypeCodes().isEmpty() || mockTestService == null) {
            throw invalidStartRequest();
        }
        var mockTest = mockTestService.requirePublished(request.mockTestId());
        var unfinished = sessionRepository
                .findFirstByStudentPublicIdAndMockTestIdAndStatusInAndDeletedFalseOrderByCreatedAtDesc(
                        caller.userId(), mockTest.getId(),
                        List.of(PracticeSessionStatus.OVERVIEW, PracticeSessionStatus.IN_PROGRESS));
        if (unfinished.isPresent() && !request.effectiveRestart()) {
            return responseMapper.toResponse(unfinished.get());
        }
        Set<String> taskCodes = new LinkedHashSet<>();
        mockTestService.itemsOf(mockTest.getId()).forEach(item -> taskCodes.add(item.getTaskCode()));
        PracticeSession session = new PracticeSession();
        session.setStudentPublicId(caller.userId());
        session.setTenantId(caller.tenantId());
        session.setProductCode(request.productCode());
        session.setTitle(mockTest.getTitle());
        session.setTimeLimitSeconds(PracticeConstants.PRACTICE_NO_SESSION_TIME_LIMIT_SECONDS);
        session.setKind(PracticeSessionKind.MOCK_TEST);
        session.setMockTestId(mockTest.getId());
        session.setTimingMode(request.effectiveTimingMode());
        session.setQuestionCount(mockTest.getItemCount());
        session.setCatalogVersion(PracticeConstants.PRACTICE_CATALOG_VERSION);
        session.setSelectedTaskTypes(joinSorted(taskCodes));
        session.setClientCapabilities(joinSorted(request.capabilities() == null
                ? Set.of() : request.capabilities().safeCapabilities()));
        session.setStartIdempotencyKey(idempotencyKey);
        session.setStartRequestHash(requestHash);
        session.setStatus(PracticeSessionStatus.OVERVIEW);
        // Restarting discards the old attempt and inserts the new one together; see replaceStart for why.
        PracticeSession saved = unfinished.isPresent()
                ? sessionPersistenceService.replaceStart(unfinished.get().getId(), clock.instant(), session)
                : sessionPersistenceService.saveStart(session);
        return responseMapper.toResponse(saved);
    }

    private Set<String> resolveTaskCodes(PracticeSessionStartRequest request) {
        if (!request.hasSkill()) {
            if (request.hasMockTest()) {
                throw invalidStartRequest();
            }
            return request.safeTaskTypeCodes();
        }
        if (!request.safeTaskTypeCodes().isEmpty() || request.hasMockTest()) {
            throw invalidStartRequest();
        }
        List<String> codes = catalogService.runnableTaskCodesForSkill(request.skillCode());
        if (codes.isEmpty()) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_CONTENT_NOT_READY, PracticeConstants.PRACTICE_CONTENT_NOT_READY_MESSAGE);
        }
        return new LinkedHashSet<>(codes);
    }

    private PracticeSessionException invalidStartRequest() {
        return new PracticeSessionException(HttpStatus.BAD_REQUEST, PracticeConstants.PRACTICE_SESSION_REQUEST_INVALID,
                PracticeConstants.PRACTICE_SESSION_REQUEST_INVALID_MESSAGE);
    }

    private void requireTimedAvailable(Collection<String> taskCodes) {
        if (timingService == null) {
            throw new PracticeSessionException(HttpStatus.UNPROCESSABLE_ENTITY,
                    PracticeConstants.PRACTICE_TIMED_MODE_UNAVAILABLE,
                    PracticeConstants.PRACTICE_TIMED_MODE_UNAVAILABLE_MESSAGE);
        }
        timingService.requireTimedAvailable(taskCodes);
    }
}
