package com.pte.scheduling.repository;

import com.pte.scheduling.domain.ProctorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProctorAssignmentRepository extends JpaRepository<ProctorAssignment, Long> {

    List<ProctorAssignment> findBySessionId(Long sessionId);
}
