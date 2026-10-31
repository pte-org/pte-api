package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /** Atomically spends a link: returns 1 for the one caller that wins, 0 for everyone presenting it afterwards. */
    @Modifying
    @Query("update PasswordResetToken token set token.usedAt = :now "
            + "where token.id = :id and token.usedAt is null and token.deleted = false")
    int markUsed(@Param("id") Long id, @Param("now") Instant now);

    /** Spends every link the user still holds, so only the newest request is ever valid. */
    @Modifying
    @Query("update PasswordResetToken token set token.usedAt = :now "
            + "where token.userId = :userId and token.usedAt is null and token.deleted = false")
    int invalidateActiveByUserId(@Param("userId") Long userId, @Param("now") Instant now);
}
