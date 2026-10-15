package com.pte.session.internal.repository;

import com.pte.session.domain.SessionIdentityVerificationCommand;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SessionIdentityVerificationCommandRepository extends JpaRepository<SessionIdentityVerificationCommand, Long> {

    Optional<SessionIdentityVerificationCommand> findByTenantIdAndSessionIdAndActorPublicIdAndRequestPublicId(
            UUID tenantId, Long sessionId, UUID actorPublicId, UUID requestPublicId);
}
