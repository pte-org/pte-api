package com.pte.shared.audit.internal.repository;

import com.pte.shared.audit.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findByTenantIdOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    /** Newest rows of the tenant without a count query (the page size bounds the result). */
    List<AuditLog> findByTenantIdAndDeletedFalseOrderByCreatedAtDesc(UUID tenantId, Pageable pageable);

    Page<AuditLog> findByTenantIdAndAggregateTypeOrderByCreatedAtDesc(UUID tenantId, String aggregateType,
            Pageable pageable);

    @Query("""
            select a from AuditLog a
             where a.tenantId = :tenantId
               and (:aggregateType is null or a.aggregateType = :aggregateType)
               and (:search is null
                    or lower(a.aggregateType) like lower(concat('%', :search, '%'))
                    or lower(a.aggregateId) like lower(concat('%', :search, '%'))
                    or lower(a.action) like lower(concat('%', :search, '%'))
                    or lower(a.summary) like lower(concat('%', :search, '%'))
                    or (:actorId is not null and a.actorUserId = :actorId))
             order by a.createdAt desc
            """)
    Page<AuditLog> findForTenant(@Param("tenantId") UUID tenantId,
            @Param("aggregateType") String aggregateType, @Param("search") String search,
            @Param("actorId") UUID actorId, Pageable pageable);
}
