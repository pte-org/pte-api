package com.pte.identity.internal.domain;

import com.pte.identity.PracticeIdentityStatus;
import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Global, verified-email identity used by the isolated student practice app. */
@Entity
@Table(name = "practice_identities", indexes = {
        @Index(name = "uk_practice_identities_email_hash", columnList = "normalized_email_hash", unique = true),
        @Index(name = "idx_practice_identities_shell_user", columnList = "shell_user_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeIdentity extends BaseEntity {

    @Column(name = "normalized_email_hash", nullable = false, length = 64)
    private String normalizedEmailHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeIdentityStatus status = PracticeIdentityStatus.ACTIVE;

    /** Internal users.id of the tenantless shell account issued after verification. */
    @Column(name = "shell_user_id")
    private Long shellUserId;

    @Column(name = "verified_at")
    private Instant verifiedAt;
}
