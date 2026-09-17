package com.pte.session.internal.repository;

import com.pte.session.domain.SessionClassAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SessionClassAssignmentRepository extends JpaRepository<SessionClassAssignment, Long> {

    boolean existsBySessionIdAndClassPublicId(Long sessionId, UUID classPublicId);

    Optional<SessionClassAssignment> findBySessionIdAndClassPublicId(Long sessionId, UUID classPublicId);

    List<SessionClassAssignment> findBySessionId(Long sessionId);
}
