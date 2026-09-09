package com.pte.admin.repository;

import com.pte.admin.domain.ClassMembership;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassMembershipRepository extends JpaRepository<ClassMembership, Long> {

    Optional<ClassMembership> findByPublicId(UUID publicId);

    boolean existsByStudentPublicId(UUID studentPublicId);

    List<ClassMembership> findByStudentPublicIdIn(List<UUID> studentPublicIds);

    /** Used by {@code StudentClass} archive/deactivate's active-members guard. */
    boolean existsByStudentClass_PublicId(UUID classPublicId);

    List<ClassMembership> findByStudentClass_PublicId(UUID classPublicId);

    /** Backs Phase 12's split — resolves only the Host-selected subset that's actually in the source Class, ignoring any stray/foreign id. */
    List<ClassMembership> findByStudentClass_PublicIdAndStudentPublicIdIn(UUID classPublicId, List<UUID> studentPublicIds);

    /**
     * Tenant-wide roster, unfiltered — the shared data source Phase 7 (search),
     * Phase 10 (bulk exam-session roster resolution) and Phase 13 (dashboard)
     * all reuse. {@code caller.tenantId()} MUST always be passed in by the
     * caller; no program-only overload of this query may ever be added (a
     * cross-tenant leak here poisons Phase 10's bulk-enroll input). The
     * {@code @EntityGraph} forces a single query with the {@code studentClass}
     * and {@code studentClass.program} associations join-fetched, so mapping
     * each row to a response never triggers a lazy load per row.
     */
    @EntityGraph(attributePaths = {"studentClass", "studentClass.program"})
    List<ClassMembership> findByStudentClass_Program_Organization_Tenant_PublicId(UUID tenantPublicId);

    /** Filtered variant of the above — both tenant and program are AND-ed in the same query. */
    @EntityGraph(attributePaths = {"studentClass", "studentClass.program"})
    List<ClassMembership> findByStudentClass_Program_Organization_Tenant_PublicIdAndStudentClass_Program_PublicId(
            UUID tenantPublicId, UUID programPublicId);
}
