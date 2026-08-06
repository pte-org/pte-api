package com.pte.common.messaging;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Generic SKIP LOCKED claim + cleanup queries every service's concrete
 * {@code OutboxRepository} inherits by declaring a one-line
 * {@code interface OutboxRepository extends OutboxJpaRepository<OutboxEntry>}
 * — no per-service SQL needed.
 *
 * <p>{@code jakarta.persistence.lock.timeout = -2} is Hibernate 6's trigger for
 * {@code FOR UPDATE SKIP LOCKED} (not a literal millisecond timeout) — verify the
 * generated SQL via Hibernate SQL logging before trusting it in a new service.
 *
 * @param <T> the service's concrete outbox entity
 */
@NoRepositoryBean
public interface OutboxJpaRepository<T extends AbstractOutboxEntry> extends JpaRepository<T, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("SELECT e FROM #{#entityName} e "
            + "WHERE e.published = false AND e.quarantined = false AND e.publishAttempts < :maxAttempts "
            + "ORDER BY e.occurredAt ASC")
    List<T> findClaimableBatch(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    @Modifying
    @Query("DELETE FROM #{#entityName} e WHERE e.published = true AND e.publishedAt < :cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);
}
