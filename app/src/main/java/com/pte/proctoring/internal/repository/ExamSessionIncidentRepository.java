package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ExamSessionIncident;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExamSessionIncidentRepository extends JpaRepository<ExamSessionIncident, Long> {

    Page<ExamSessionIncident> findBySessionPublicIdAndTenantIdAndDeletedFalse(
            UUID sessionPublicId, UUID tenantId, Pageable pageable);

    Page<ExamSessionIncident> findBySessionPublicIdAndTenantIdAndStatusAndDeletedFalse(
            UUID sessionPublicId, UUID tenantId, String status, Pageable pageable);

    Optional<ExamSessionIncident> findByPublicIdAndSessionPublicIdAndTenantIdAndDeletedFalse(
            UUID publicId, UUID sessionPublicId, UUID tenantId);
}
