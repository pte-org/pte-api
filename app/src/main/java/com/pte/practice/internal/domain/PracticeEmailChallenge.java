package com.pte.practice.internal.domain;

import com.pte.practice.internal.constant.PracticeConstants;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One hashed, single-use email verification challenge. */
@Entity
@Table(name = "practice_email_challenges", indexes = {
        @Index(name = "idx_practice_email_challenges_identity_created", columnList = "identity_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeEmailChallenge extends BaseEntity {

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "code_hash", nullable = false, length = 255)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    public boolean isConsumedOrExpired(Instant now) {
        return consumedAt != null || !expiresAt.isAfter(now)
                || failedAttempts >= PracticeConstants.MAX_VERIFICATION_ATTEMPTS;
    }
}
