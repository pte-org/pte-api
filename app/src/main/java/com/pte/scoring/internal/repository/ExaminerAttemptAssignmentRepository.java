package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAttemptAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ExaminerAttemptAssignmentRepository extends JpaRepository<ExaminerAttemptAssignment, Long> {

    boolean existsByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId);

    List<ExaminerAttemptAssignment> findByTenantIdAndSessionPublicIdAndExaminerPublicId(
            UUID tenantId, UUID sessionPublicId, UUID examinerPublicId);

    List<ExaminerAttemptAssignment> findByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId,
            UUID sessionPublicId);

    List<ExaminerAttemptAssignment> findByTenantIdAndSessionPublicIdAndAttemptPublicIdIn(
            UUID tenantId, UUID sessionPublicId, List<UUID> attemptPublicIds);

    long countByTenantIdAndSessionPublicIdAndDeletedFalse(UUID tenantId, UUID sessionPublicId);

    @Query("select a.examinerPublicId as examinerPublicId, count(a.id) as attemptCount, "
            + "coalesce(sum(a.eligibleAnswerCount), 0) as eligibleAnswerCount "
            + "from ExaminerAttemptAssignment a where a.tenantId = :tenantId "
            + "and a.sessionPublicId = :sessionPublicId and a.deleted = false "
            + "group by a.examinerPublicId order by a.examinerPublicId")
    List<ExaminerAssignmentLoadProjection> summarizeLoads(@Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId);

    interface ExaminerAssignmentLoadProjection {
        UUID getExaminerPublicId();
        long getAttemptCount();
        long getEligibleAnswerCount();
    }
}
