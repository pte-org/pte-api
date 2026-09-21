package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ClassMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassMembershipRepository extends JpaRepository<ClassMembership, Long> {

    Optional<ClassMembership> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    boolean existsByStudentPublicIdAndTenantId(UUID studentPublicId, UUID tenantId);

    List<ClassMembership> findByTenantIdAndStudentPublicIdIn(UUID tenantId, List<UUID> studentPublicIds);

    /** Used by {@code StudentClass} archive/deactivate's active-members guard. */
    boolean existsByTenantIdAndStudentClass_PublicId(UUID tenantId, UUID classPublicId);

    List<ClassMembership> findByTenantIdAndStudentClass_PublicId(UUID tenantId, UUID classPublicId);

    /** Backs Phase 12's split â€” resolves only the Host-selected subset that's actually in the source Class, ignoring any stray/foreign id. */
    List<ClassMembership> findByTenantIdAndStudentClass_PublicIdAndStudentPublicIdIn(
            UUID tenantId, UUID classPublicId, List<UUID> studentPublicIds);

    /**
     * Tenant-wide roster, unfiltered â€” the shared data source Phase 7 (search),
     * Phase 10 (bulk exam-session roster resolution) and Phase 13 (dashboard)
     * all reuse. {@code caller.tenantId()} MUST always be passed in by the
     * caller; no program-only overload of this query may ever be added (a
     * cross-tenant leak here poisons Phase 10's bulk-enroll input). The
     * {@code @EntityGraph} forces a single query with the {@code studentClass}
     * and {@code studentClass.program} associations join-fetched, so mapping
     * each row to a response never triggers a lazy load per row.
     */
    @EntityGraph(attributePaths = {"studentClass", "studentClass.program"})
    @Query("""
            select m from ClassMembership m
            join fetch m.studentClass c
            join fetch c.program p
            join p.organization o
            where m.tenantId = :tenantId
              and m.deleted = false
              and c.deleted = false
              and c.status = com.pte.enrollment.domain.enums.ClassStatus.ACTIVE
              and p.deleted = false
              and p.status = com.pte.enrollment.domain.enums.ProgramStatus.ACTIVE
              and (p.startDate is null or p.startDate <= CURRENT_DATE)
              and (p.endDate is null or p.endDate >= CURRENT_DATE)
              and o.tenant.publicId = :tenantId
            """)
    List<ClassMembership> findByStudentClass_Program_Organization_Tenant_PublicId(
            @Param("tenantId") UUID tenantPublicId);

    /** Filtered variant of the above â€” both tenant and program are AND-ed in the same query. */
    @EntityGraph(attributePaths = {"studentClass", "studentClass.program"})
    @Query("""
            select m from ClassMembership m
            join fetch m.studentClass c
            join fetch c.program p
            join p.organization o
            where m.tenantId = :tenantId
              and m.deleted = false
              and c.deleted = false
              and c.status = com.pte.enrollment.domain.enums.ClassStatus.ACTIVE
              and p.deleted = false
              and p.status = com.pte.enrollment.domain.enums.ProgramStatus.ACTIVE
              and (p.startDate is null or p.startDate <= CURRENT_DATE)
              and (p.endDate is null or p.endDate >= CURRENT_DATE)
              and o.tenant.publicId = :tenantId
              and p.publicId = :programPublicId
            """)
    List<ClassMembership> findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
            @Param("tenantId") UUID tenantPublicId, @Param("programPublicId") UUID programPublicId);
}
