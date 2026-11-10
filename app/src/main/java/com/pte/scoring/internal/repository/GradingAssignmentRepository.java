package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.GradingAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GradingAssignmentRepository extends JpaRepository<GradingAssignment, Long> {

    org.springframework.data.domain.Slice<StaffAssignmentRow>
            findByTenantIdAndExaminerPublicIdAndSessionPublicIdInAndDeletedFalse(
                    UUID tenantId, UUID examinerPublicId, List<UUID> sessionPublicIds,
                    org.springframework.data.domain.Pageable pageable);

    boolean existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId);

    java.util.Optional<GradingAssignment> findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId);

    java.util.Optional<GradingAssignment> findByTenantIdAndPublicIdAndDeletedFalse(
            UUID tenantId, UUID publicId);

    List<GradingAssignment> findByTenantIdAndSessionPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID examinerPublicId);

    java.util.Optional<GradingAssignment> findByTenantIdAndSessionPublicIdAndAttemptPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId);

    List<GradingAssignment> findByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId,
            UUID sessionPublicId);

    List<GradingAssignment> findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(
            UUID tenantId, UUID sessionPublicId, List<UUID> attemptPublicIds);

    long countByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId, UUID sessionPublicId);

    @Query("select a.examinerPublicId as examinerPublicId, count(a.id) as attemptCount, "
            + "coalesce(sum(a.eligibleAnswerCount), 0) as eligibleAnswerCount "
            + "from GradingAssignment a where a.tenantId = :tenantId "
            + "and a.sessionPublicId = :sessionPublicId and a.deleted = false "
            + "group by a.examinerPublicId order by a.examinerPublicId")
    List<ExaminerAssignmentLoadProjection> summarizeLoads(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);

    /** Locks one assignment for the examiner workflow command that is about to change its progress. */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from GradingAssignment a where a.tenantId = :tenantId and a.publicId = :publicId "
            + "and a.deleted = false")
    java.util.Optional<GradingAssignment> findForUpdate(@Param("tenantId") UUID tenantId,
            @Param("publicId") UUID publicId);

    interface ExaminerAssignmentLoadProjection {
        UUID getExaminerPublicId();
        long getAttemptCount();
        long getEligibleAnswerCount();
    }
}
