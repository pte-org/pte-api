package com.pte.practice.internal.service.session;

import com.pte.identity.PracticeIdentityService;
import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.practice.internal.domain.PracticeSession;
import com.pte.practice.internal.domain.enums.PracticeSessionStatus;
import com.pte.practice.internal.dto.request.PracticeSessionStartRequest;
import com.pte.practice.internal.dto.response.PracticeSessionResponse;
import com.pte.practice.internal.exception.PracticeNotEntitledException;
import com.pte.practice.internal.exception.PracticeSessionException;
import com.pte.practice.internal.mapper.PracticeSessionResponseMapper;
import com.pte.practice.internal.repository.PracticeSessionRepository;
import com.pte.practice.internal.service.PracticeCatalogService;
import com.pte.practice.internal.service.PracticeEntitlementService;
import com.pte.shared.security.CurrentUser;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

import java.util.Set;
import java.util.UUID;

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
    private final PracticeIdentityService identityService;

    PracticeSessionStarter(PracticeSessionRepository sessionRepository,
            PracticeSessionPersistenceService sessionPersistenceService, PracticeCatalogService catalogService,
            PracticeSessionResponseMapper responseMapper, PracticeEntitlementService entitlementService,
            PracticeIdentityService identityService) {
        this.sessionRepository = sessionRepository;
        this.sessionPersistenceService = sessionPersistenceService;
        this.catalogService = catalogService;
        this.responseMapper = responseMapper;
        this.entitlementService = entitlementService;
        this.identityService = identityService;
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

    private void requireActiveMembership(PracticeIdentityService.PracticeIdentityView identity,
            UUID organizationId) {
        boolean linked = identity.memberships().stream()
                .anyMatch(membership -> organizationId.equals(membership.tenantId())
                        && membership.isActiveStudent());
        if (!linked) {
            throw new PracticeNotEntitledException();
        }
    }
}
