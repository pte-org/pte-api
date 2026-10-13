package com.pte.support.internal.repository;

import com.pte.support.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
