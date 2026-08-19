package com.pte.iam.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A hashed refresh token. Only the hash is stored — the raw token is returned to
 * the client once and never persisted. Rotation revokes the old row and issues a
 * new one.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_user", columnList = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class RefreshToken extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public boolean isActive(Instant now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
