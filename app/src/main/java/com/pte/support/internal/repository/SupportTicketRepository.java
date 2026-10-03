package com.pte.support.internal.repository;

import com.pte.support.domain.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long>,
        JpaSpecificationExecutor<SupportTicket> {

    Optional<SupportTicket> findByPublicId(UUID publicId);

    Optional<SupportTicket> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);
}
