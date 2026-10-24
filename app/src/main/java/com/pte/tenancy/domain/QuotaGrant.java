package com.pte.tenancy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One append-only seat grant in an organization's quota history. {@code amount} is the delta added to the
 * organization's student limit, never a running total; it lives inside the Organization aggregate so the limit and
 * its history are saved together.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class QuotaGrant {

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "package_name", nullable = false, updatable = false)
    private String packageName;

    @Column(nullable = false, updatable = false)
    private int amount;

    @Column(name = "actor_user_id", nullable = false, updatable = false)
    private UUID actorUserId;

    @Column(updatable = false)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    QuotaGrant(String packageName, int amount, UUID actorUserId, String note, Instant createdAt) {
        this.publicId = UUID.randomUUID();
        this.packageName = packageName;
        this.amount = amount;
        this.actorUserId = actorUserId;
        this.note = note;
        this.createdAt = createdAt;
    }
}
