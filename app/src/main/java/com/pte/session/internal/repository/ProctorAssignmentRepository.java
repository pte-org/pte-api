package com.pte.session.internal.repository;

import com.pte.session.domain.ProctorAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProctorAssignmentRepository extends JpaRepository<ProctorAssignment, Long> {

    List<ProctorAssignment> findBySessionId(Long sessionId);

    boolean existsBySessionIdAndProctorPublicId(Long sessionId, UUID proctorPublicId);

    Optional<ProctorAssignment> findByPublicId(UUID publicId);
}
