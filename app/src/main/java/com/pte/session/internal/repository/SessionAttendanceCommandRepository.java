package com.pte.session.internal.repository;

import com.pte.session.domain.SessionAttendanceCommand;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SessionAttendanceCommandRepository extends JpaRepository<SessionAttendanceCommand, Long> {

    Optional<SessionAttendanceCommand> findByTenantIdAndSessionIdAndActorPublicIdAndRequestPublicId(
            UUID tenantId, Long sessionId, UUID actorPublicId, UUID requestPublicId);

    @Query("SELECT c FROM SessionAttendanceCommand c "
            + "WHERE c.tenantId = :tenantId AND c.session.publicId = :sessionPublicId "
            + "AND c.actorPublicId = :actorPublicId AND c.requestPublicId = :requestPublicId")
    Optional<SessionAttendanceCommand> findReceipt(
            @Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("actorPublicId") UUID actorPublicId,
            @Param("requestPublicId") UUID requestPublicId);

    @Query("SELECT c FROM SessionAttendanceCommand c "
            + "WHERE c.tenantId = :tenantId AND c.session.publicId = :sessionPublicId "
            + "AND c.studentPublicId = :studentPublicId AND c.changed = true "
            + "ORDER BY c.serverActionAt DESC, c.id DESC")
    Page<SessionAttendanceCommand> findHistory(
            @Param("tenantId") UUID tenantId,
            @Param("sessionPublicId") UUID sessionPublicId,
            @Param("studentPublicId") UUID studentPublicId,
            Pageable pageable);
}
