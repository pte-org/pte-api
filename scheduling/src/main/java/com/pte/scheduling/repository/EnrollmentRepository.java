package com.pte.scheduling.repository;

import com.pte.scheduling.domain.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    List<Enrollment> findBySessionId(Long sessionId);
}
