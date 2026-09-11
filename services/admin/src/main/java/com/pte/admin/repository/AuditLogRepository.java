package com.pte.admin.repository;

import com.pte.admin.domain.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<AuditLog> findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(UUID tenantId, String aggregateType);
}
