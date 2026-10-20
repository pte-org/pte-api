package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAnswerDraft;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminerAnswerDraftRepository extends JpaRepository<ExaminerAnswerDraft, Long> {

    List<ExaminerAnswerDraft> findByTenantIdAndWorkIdAndDeletedFalse(UUID tenantId, Long workId);

    /** Saved drafts per grading work, for the host's per-attempt examiner progress. */
    @Query("SELECT new com.pte.scoring.internal.repository.WorkDraftCount(d.workId, COUNT(d)) "
            + "FROM ExaminerAnswerDraft d WHERE d.tenantId = :tenantId AND d.workId IN :workIds "
            + "AND d.deleted = false GROUP BY d.workId")
    List<WorkDraftCount> countByWork(@Param("tenantId") UUID tenantId, @Param("workIds") List<Long> workIds);

    Optional<ExaminerAnswerDraft> findByTenantIdAndWorkIdAndAnswerPublicIdAndDeletedFalse(
            UUID tenantId, Long workId, UUID answerPublicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM ExaminerAnswerDraft d WHERE d.tenantId = :tenantId AND d.workId = :workId AND d.answerPublicId = :answerPublicId AND d.deleted = false")
    Optional<ExaminerAnswerDraft> findByWorkAndAnswerWithLock(
            @Param("tenantId") UUID tenantId,
            @Param("workId") Long workId,
            @Param("answerPublicId") UUID answerPublicId);
}
