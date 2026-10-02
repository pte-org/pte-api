package com.pte.session.internal.repository;

import com.pte.session.domain.ExamAudienceMember;
import com.pte.session.domain.enums.AudienceMemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamAudienceMemberRepository extends JpaRepository<ExamAudienceMember, Long> {

    List<ExamAudienceMember> findBySessionIdOrderByStudentPublicIdAsc(Long sessionId);

    List<ExamAudienceMember> findBySessionIdAndStatus(Long sessionId, AudienceMemberStatus status);

    long countBySessionIdAndStatus(Long sessionId, AudienceMemberStatus status);

    void deleteBySessionId(Long sessionId);
}
