package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProctorSessionRepository extends JpaRepository<ProctorSession, Long> {

    Optional<ProctorSession> findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
            UUID sessionPublicId, UUID proctorPublicId, UUID tenantId, ProctorSessionStatus status);

    Optional<ProctorSession> findByPublicIdAndProctorPublicIdAndTenantId(UUID publicId, UUID proctorPublicId, UUID tenantId);
}
