package com.pte.session.internal.repository;

import com.pte.session.domain.SessionAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessionAttendanceRepository extends JpaRepository<SessionAttendance, Long> {

    Optional<SessionAttendance> findBySessionIdAndStudentPublicId(Long sessionId, UUID studentPublicId);

    @Query("SELECT sa FROM SessionAttendance sa WHERE sa.session.publicId = :sessionPublicId AND sa.studentPublicId = :studentPublicId AND sa.tenantId = :tenantId AND sa.deleted = false")
    Optional<SessionAttendance> findBySessionPublicIdAndStudentPublicIdAndTenantId(
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("studentPublicId") UUID studentPublicId,
            @Param("tenantId") UUID tenantId);
}
