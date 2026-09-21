package com.pte.session.internal.repository;

import com.pte.session.domain.ExamForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamFormRepository extends JpaRepository<ExamForm, Long> {

    List<ExamForm> findBySessionIdOrderByFormIndexAsc(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
