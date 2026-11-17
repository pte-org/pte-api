package com.pte.identity.internal.repository;

import com.pte.identity.internal.domain.RefreshToken;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Claims one token for good: 1 if this call revoked it, 0 if someone else already had. Atomic in the database. */
    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.id = :id and token.revoked = false and token.deleted = false")
    int revokeIfActive(@Param("id") Long id);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.userId = :userId and token.revoked = false and token.deleted = false")
    int revokeAllActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.userId = :userId and token.id <> :keepId and token.revoked = false and token.deleted = false")
    int revokeAllActiveByUserIdExceptId(@Param("userId") Long userId, @Param("keepId") Long keepId);

    @Modifying
    @Query("update RefreshToken token set token.revoked = true "
            + "where token.userId = :userId and token.organizationId = :organizationId "
            + "and token.revoked = false and token.deleted = false")
    int revokeAllActiveByUserIdAndOrganizationId(@Param("userId") Long userId,
            @Param("organizationId") UUID organizationId);
}
