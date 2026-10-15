package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.userId = :userId and token.revoked = false and token.deleted = false")
    int revokeAllActiveByUserId(@Param("userId") Long userId);
}
