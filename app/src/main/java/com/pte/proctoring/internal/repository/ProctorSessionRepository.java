package com.pte.proctoring.internal.repository;

import com.pte.proctoring.domain.ProctorSession;
import com.pte.proctoring.domain.enums.ProctorSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ProctorSessionRepository extends JpaRepository<ProctorSession, Long> {

    Optional<ProctorSession> findBySessionPublicIdAndProctorPublicIdAndTenantIdAndStatus(
            UUID sessionPublicId, UUID proctorPublicId, UUID tenantId, ProctorSessionStatus status);

    Optional<ProctorSession> findByPublicIdAndProctorPublicIdAndTenantId(UUID publicId, UUID proctorPublicId, UUID tenantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ps FROM ProctorSession ps WHERE ps.publicId = :publicId AND ps.proctorPublicId = :proctorPublicId AND ps.tenantId = :tenantId")
    Optional<ProctorSession> findWithLockByPublicIdAndProctorPublicIdAndTenantId(
            @Param("publicId") UUID publicId,
            @Param("proctorPublicId") UUID proctorPublicId,
            @Param("tenantId") UUID tenantId);
}
