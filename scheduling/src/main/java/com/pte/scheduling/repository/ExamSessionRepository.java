package com.pte.scheduling.repository;

import com.pte.scheduling.domain.ExamSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSessionRepository extends JpaRepository<ExamSession, Long> {

    @EntityGraph(attributePaths = "composition")
    Optional<ExamSession> findWithCompositionByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    Optional<ExamSession> findByPublicIdAndTenantId(UUID publicId, UUID tenantId);

    @EntityGraph(attributePaths = "composition")
    List<ExamSession> findByTenantId(UUID tenantId);
}
