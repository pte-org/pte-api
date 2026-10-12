package com.pte.scoring.internal.repository;

import com.pte.scoring.domain.ExaminerGradingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ExaminerGradingEventRepository extends JpaRepository<ExaminerGradingEvent, Long> {

    List<ExaminerGradingEvent> findByTenantIdAndWorkIdOrderByOccurredAtAsc(UUID tenantId, Long workId);

    Page<ExaminerGradingEvent> findByTenantIdAndWorkId(UUID tenantId, Long workId, Pageable pageable);
}
