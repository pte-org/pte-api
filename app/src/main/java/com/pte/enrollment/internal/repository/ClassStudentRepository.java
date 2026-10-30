package com.pte.enrollment.internal.repository;

import com.pte.enrollment.domain.ClassStudent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClassStudentRepository extends JpaRepository<ClassStudent, Long> {

    Optional<ClassStudent> findByPublicIdAndOrganizationId(UUID publicId, UUID tenantId);

    List<ClassStudent> findByOrganizationIdAndStudentPublicIdIn(UUID tenantId, List<UUID> studentPublicIds);

    /** Current assignment read for the tenant student workspace. */
    @EntityGraph(attributePaths = {"trainingClass", "trainingClass.program"})
    Optional<ClassStudent> findByOrganizationIdAndStudentPublicId(UUID tenantId, UUID studentPublicId);

    /** Overview: learners currently placed in a (non-deleted) class of the tenant. */
    @Query("SELECT COUNT(m) FROM ClassStudent m JOIN m.trainingClass c "
            + "WHERE m.organizationId = :tenantId AND m.deleted = false AND c.deleted = false")
    long countAssignedForTenant(@Param("tenantId") UUID tenantId);

    /** Used by {@code TrainingClass} archive/deactivate's active-members guard. */
    boolean existsByOrganizationIdAndTrainingClass_PublicId(UUID tenantId, UUID classPublicId);

    List<ClassStudent> findByOrganizationIdAndTrainingClass_PublicId(UUID tenantId, UUID classPublicId);

    /** Backs Phase 12's split â€” resolves only the Host-selected subset that's actually in the source Class, ignoring any stray/foreign id. */
    List<ClassStudent> findByOrganizationIdAndTrainingClass_PublicIdAndStudentPublicIdIn(
            UUID tenantId, UUID classPublicId, List<UUID> studentPublicIds);

    /** Management roster includes future and inactive assignments, with optional program scope. */
    @Query("""
            select m from ClassStudent m
            join fetch m.trainingClass c
            join fetch c.program p
            join p.organization o
            where m.organizationId = :tenantId
              and c.organizationId = :tenantId
              and o.publicId = :tenantId
              and m.deleted = false
              and c.deleted = false
              and p.deleted = false
              and (:programPublicId is null or p.publicId = :programPublicId)
            """)
    List<ClassStudent> findManagementMemberships(
            @Param("tenantId") UUID tenantId, @Param("programPublicId") UUID programPublicId);

    /** Eligible roster for active classes and programs within the current program date range. */
    @EntityGraph(attributePaths = {"trainingClass", "trainingClass.program"})
    @Query("""
            select m from ClassStudent m
            join fetch m.trainingClass c
            join fetch c.program p
            join p.organization o
            where m.organizationId = :tenantId
              and m.deleted = false
              and c.deleted = false
              and c.status = com.pte.enrollment.domain.enums.ClassStatus.ACTIVE
              and p.deleted = false
              and p.status = com.pte.enrollment.domain.enums.ProgramStatus.ACTIVE
              and (p.startDate is null or p.startDate <= CURRENT_DATE)
              and (p.endDate is null or p.endDate >= CURRENT_DATE)
              and o.publicId = :tenantId
            """)
    List<ClassStudent> findByTrainingClass_Program_Organization_PublicId(
            @Param("tenantId") UUID tenantPublicId);

    /** Filtered variant of the above â€” both tenant and program are AND-ed in the same query. */
    @EntityGraph(attributePaths = {"trainingClass", "trainingClass.program"})
    @Query("""
            select m from ClassStudent m
            join fetch m.trainingClass c
            join fetch c.program p
            join p.organization o
            where m.organizationId = :tenantId
              and m.deleted = false
              and c.deleted = false
              and c.status = com.pte.enrollment.domain.enums.ClassStatus.ACTIVE
              and p.deleted = false
              and p.status = com.pte.enrollment.domain.enums.ProgramStatus.ACTIVE
              and (p.startDate is null or p.startDate <= CURRENT_DATE)
              and (p.endDate is null or p.endDate >= CURRENT_DATE)
              and o.publicId = :tenantId
              and p.publicId = :programPublicId
            """)
    List<ClassStudent> findByTrainingClass_Program_Organization_PublicIdAndTrainingClass_Program_PublicId(
            @Param("tenantId") UUID tenantPublicId, @Param("programPublicId") UUID programPublicId);
}
