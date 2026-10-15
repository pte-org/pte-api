package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerAnswerScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminerAnswerScoreRepository extends JpaRepository<ExaminerAnswerScore, Long> {

    List<StaffScoreRow> findByTenantIdAndAnswerPublicIdInAndDeletedFalse(UUID tenantId, List<UUID> answerIds);

    @org.springframework.data.jpa.repository.Query("select s.attemptPublicId as attemptPublicId, count(s.id) as scoreCount "
            + "from ExaminerAnswerScore s where s.tenantId = :tenantId and s.sessionPublicId in :sessionIds "
            + "and s.examinerPublicId = :examinerId and s.attemptPublicId in :attemptIds and s.deleted = false "
            + "group by s.attemptPublicId")
    List<StaffScoreCount> countStaffScores(@org.springframework.data.repository.query.Param("tenantId") UUID tenantId,
            @org.springframework.data.repository.query.Param("sessionIds") List<UUID> sessionIds,
            @org.springframework.data.repository.query.Param("examinerId") UUID examinerId,
            @org.springframework.data.repository.query.Param("attemptIds") List<UUID> attemptIds);

    interface StaffScoreCount { UUID getAttemptPublicId(); long getScoreCount(); }

    Optional<ExaminerAnswerScore> findByAnswerPublicIdAndTenantId(UUID answerPublicId, UUID tenantId);

    List<ExaminerAnswerScore> findByTenantIdAndSessionPublicId(UUID tenantId, UUID sessionPublicId);

    List<ExaminerAnswerScore> findByTenantIdAndSessionPublicIdAndAttemptPublicId(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId);

    Optional<ExaminerAnswerScore> findByTenantIdAndExaminerPublicIdAndRequestPublicId(
            UUID tenantId, UUID examinerPublicId, UUID requestPublicId);

    Optional<ExaminerAnswerScore> findByTenantIdAndRequestPublicId(UUID tenantId, UUID requestPublicId);

    long countByTenantIdAndSessionPublicIdAndAttemptPublicIdAndExaminerPublicIdAndDeletedFalse(
            UUID tenantId, UUID sessionPublicId, UUID attemptPublicId, UUID examinerPublicId);
}
