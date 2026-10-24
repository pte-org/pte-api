package com.pte.practice.internal.repository;

import com.pte.practice.internal.domain.OtpChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    long countByIdentityIdAndCreatedAtAfterAndDeletedFalse(Long identityId, Instant createdAfter);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from OtpChallenge challenge "
            + "where challenge.publicId = :publicId and challenge.deleted = false")
    Optional<OtpChallenge> findWithLockByPublicIdAndDeletedFalse(@Param("publicId") UUID publicId);
}
