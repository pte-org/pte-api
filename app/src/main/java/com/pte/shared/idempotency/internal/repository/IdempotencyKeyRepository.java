package com.pte.shared.idempotency.internal.repository;

import com.pte.shared.idempotency.domain.IdempotencyKey;
import com.pte.shared.idempotency.domain.IdempotencyScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    @Query("select k from IdempotencyKey k where k.scope = :scope and k.key = :key "
            + "and k.organizationId = :organizationId")
    Optional<IdempotencyKey> findByScopeAndKey(@Param("scope") IdempotencyScope scope, @Param("key") String key,
            @Param("organizationId") UUID organizationId);

    @Modifying
    @Query("delete from IdempotencyKey k where k.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
