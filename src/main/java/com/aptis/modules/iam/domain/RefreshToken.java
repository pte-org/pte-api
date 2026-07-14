package com.aptis.modules.iam.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.domain.BaseEntity;

@Entity
@Table(name = "refresh_token")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class RefreshToken extends BaseEntity {

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 20)
    private String userType;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    public RefreshToken(
            String tokenHash,
            Long userId,
            String userType,
            LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.userId = userId;
        this.userType = userType;
        this.expiresAt = expiresAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(LocalDateTime currentTime) {
        return !expiresAt.isAfter(currentTime);
    }

    public void revoke(LocalDateTime revokedTime) {
        this.revokedAt = revokedTime;
    }
}
