package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ExamSessionIncidentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ExamSessionIncidentHistoryRepository extends JpaRepository<ExamSessionIncidentHistory, Long> {

    List<ExamSessionIncidentHistory> findByIncidentPublicIdAndDeletedFalseOrderByCreatedAtAsc(UUID incidentPublicId);
}
