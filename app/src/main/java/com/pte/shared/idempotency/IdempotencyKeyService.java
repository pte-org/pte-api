package com.pte.shared.idempotency;

import com.pte.shared.idempotency.domain.IdempotencyKey;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import com.pte.shared.idempotency.internal.constant.IdempotencyConstants;
import com.pte.shared.idempotency.internal.exception.IdempotencyKeyInFlightException;
import com.pte.shared.idempotency.internal.repository.IdempotencyKeyRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Dedupe + receipt store shared by every command family that must tolerate client retries. */
@Service
public class IdempotencyKeyService {

    private final IdempotencyKeyRepository repository;
    private final Clock clock;

    public IdempotencyKeyService(IdempotencyKeyRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param anchor the instant retention counts from: session close time for session-bound
     *               scopes, request time otherwise
     */
    @Transactional
    public IdempotencyDecision begin(IdempotencyScope scope, String key, UUID organizationId, String requestHash,
            Instant anchor) {
        UUID owner = ownerOf(organizationId);
        return repository.findByScopeAndKey(scope, key, owner)
                .map(existing -> decide(existing, requestHash))
                .orElseGet(() -> create(scope, key, owner, requestHash, anchor));
    }

    @Transactional
    public void complete(IdempotencyScope scope, String key, UUID organizationId, String resultJson) {
        IdempotencyKey existing = repository.findByScopeAndKey(scope, key, ownerOf(organizationId))
                .orElseThrow(() -> new IllegalStateException(
                        IdempotencyConstants.IDEMPOTENCY_KEY_NOT_FOUND.formatted(key)));
        existing.setResult(resultJson);
        existing.setCompleted(true);
    }

    /** The stored receipt of a completed command, or empty when the key is unknown, in flight or expired. */
    @Transactional(readOnly = true)
    public Optional<String> findResult(IdempotencyScope scope, String key, UUID organizationId) {
        return repository.findByScopeAndKey(scope, key, ownerOf(organizationId))
                .filter(IdempotencyKey::isCompleted)
                .map(IdempotencyKey::getResult);
    }

    @Transactional
    public long purgeExpired(Instant now) {
        return repository.deleteExpired(now);
    }

    @Scheduled(cron = IdempotencyConstants.PURGE_CRON)
    void purgeExpiredOnSchedule() {
        purgeExpired(clock.instant());
    }

    private IdempotencyDecision decide(IdempotencyKey existing, String requestHash) {
        if (!existing.getRequestHash().equals(requestHash) || !existing.isCompleted()) {
            return IdempotencyDecision.conflict();
        }
        return IdempotencyDecision.replay(existing.getResult());
    }

    private IdempotencyDecision create(IdempotencyScope scope, String key, UUID organizationId, String requestHash,
            Instant anchor) {
        IdempotencyKey created = new IdempotencyKey();
        created.setScope(scope);
        created.setKey(key);
        created.setOrganizationId(organizationId);
        created.setRequestHash(requestHash);
        created.setExpiresAt(scope.expiresAt(anchor));
        try {
            // Flush now so a concurrent claim of the same key surfaces here, not at commit.
            repository.saveAndFlush(created);
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint is the first line of defence: the winner owns the key. This
            // transaction is already doomed, so signal 409 and let the client retry for the receipt.
            throw new IdempotencyKeyInFlightException();
        }
        return IdempotencyDecision.created();
    }

    private UUID ownerOf(UUID organizationId) {
        return organizationId == null ? IdempotencyConstants.PLATFORM_ORGANIZATION_ID : organizationId;
    }
}
