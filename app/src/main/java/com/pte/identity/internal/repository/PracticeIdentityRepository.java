package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.PracticeIdentity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PracticeIdentityRepository extends JpaRepository<PracticeIdentity, Long> {

    Optional<PracticeIdentity> findByNormalizedEmailHashAndDeletedFalse(String normalizedEmailHash);

    Optional<PracticeIdentity> findByShellUserIdAndDeletedFalse(Long shellUserId);

    Optional<PracticeIdentity> findByIdAndDeletedFalse(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select identity from PracticeIdentity identity where identity.id = :id and identity.deleted = false")
    Optional<PracticeIdentity> findWithLockByIdAndDeletedFalse(@Param("id") Long id);
}
