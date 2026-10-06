package com.pte.billing.internal.repository;

import com.pte.billing.domain.LicenseIssueIntent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LicenseIssueIntentRepository extends JpaRepository<LicenseIssueIntent, Long> {
    Optional<LicenseIssueIntent> findByActorPublicIdAndOperationAndIdempotencyKey(
            UUID actorPublicId, String operation, UUID idempotencyKey);
}
