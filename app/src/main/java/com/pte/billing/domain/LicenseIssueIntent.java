package com.pte.billing.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

/** Retains successful issuance identity without storing bearer codes or request bodies. */
@Entity
@Table(name = "license_issue_intents", uniqueConstraints = @UniqueConstraint(
        name = "uk_license_issue_intents_actor_operation_key",
        columnNames = {"actor_public_id", "operation", "idempotency_key"}))
@Getter
@NoArgsConstructor
public class LicenseIssueIntent extends BaseEntity {
    @Column(nullable = false, updatable = false)
    private UUID actorPublicId;
    @Column(nullable = false, updatable = false, length = 64)
    private String operation;
    @Column(nullable = false, updatable = false)
    private UUID idempotencyKey;
    @Column(nullable = false, updatable = false, length = 64)
    private String payloadFingerprint;
    @Column(nullable = false, updatable = false)
    private UUID licenseCodePublicId;

    public static LicenseIssueIntent create(UUID actor, String operation, UUID key, String fingerprint, UUID result) {
        LicenseIssueIntent intent = new LicenseIssueIntent();
        intent.actorPublicId = actor;
        intent.operation = operation;
        intent.idempotencyKey = key;
        intent.payloadFingerprint = fingerprint;
        intent.licenseCodePublicId = result;
        return intent;
    }
}
