package com.pte.identity.internal.domain;

import com.pte.identity.PracticeMembershipStatus;
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
import java.util.UUID;

/** Durable link from a verified email identity to an imported tenant student. */
@Entity
@Table(name = "practice_identity_memberships", indexes = {
        @Index(name = "uk_practice_identity_memberships_link", columnList = "identity_id,user_public_id", unique = true),
        @Index(name = "idx_practice_identity_memberships_identity", columnList = "identity_id"),
        @Index(name = "idx_practice_identity_memberships_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class PracticeIdentityMembership extends BaseEntity {

    @Column(name = "identity_id", nullable = false)
    private Long identityId;

    @Column(name = "user_public_id", nullable = false)
    private UUID userPublicId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PracticeMembershipStatus status = PracticeMembershipStatus.ACTIVE;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;
}
