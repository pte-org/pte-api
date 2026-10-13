package com.pte.support.internal.repository;

import com.pte.support.domain.SupportTicket;
import com.pte.support.domain.enums.TicketStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>,
        JpaSpecificationExecutor<SupportTicket> {

    Optional<SupportTicket> findByPublicId(UUID publicId);

    Optional<SupportTicket> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    /** Overview: non-deleted tickets of the tenant grouped by status (counts only). */
    @Query("SELECT t.status, COUNT(t) FROM SupportTicket t "
            + "WHERE t.tenantId = :tenantId AND t.deleted = false GROUP BY t.status")
    List<Object[]> countByTenantGroupedByStatus(@Param("tenantId") UUID tenantId);

    /** Overview (platform-wide): non-deleted tickets of every tenant grouped by status. */
    @Query("SELECT t.status, COUNT(t) FROM SupportTicket t WHERE t.deleted = false GROUP BY t.status")
    List<Object[]> countGroupedByStatus();

    /** Overview (platform-wide): tickets opened in {@code [from, to)}. */
    @Query("SELECT COUNT(t) FROM SupportTicket t WHERE t.deleted = false "
            + "AND t.createdAt >= :from AND t.createdAt < :to")
    long countCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);

    /** Overview (platform-wide): oldest tickets in {@code status} first, bounded by {@code pageable}. */
    @Query("SELECT t FROM SupportTicket t WHERE t.deleted = false AND t.status = :status "
            + "ORDER BY t.createdAt ASC, t.id ASC")
    List<SupportTicket> findOldestByStatus(@Param("status") TicketStatus status, Pageable pageable);
}
