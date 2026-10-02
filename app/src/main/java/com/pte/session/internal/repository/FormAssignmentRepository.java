package com.pte.session.internal.repository;

import com.pte.session.domain.FormAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FormAssignmentRepository extends JpaRepository<FormAssignment, Long> {

    Optional<FormAssignment> findBySessionIdAndStudentPublicId(Long sessionId, UUID studentPublicId);

    long countBySessionId(Long sessionId);

    void deleteBySessionId(Long sessionId);
}
