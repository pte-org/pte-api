package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ScoreSourceAudit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScoreSourceAuditRepository extends JpaRepository<ScoreSourceAudit, Long> {

    List<ScoreSourceAudit> findByTenantIdAndSessionPublicIdOrderByOccurredAtDesc(
            UUID tenantId, UUID sessionPublicId);
}
