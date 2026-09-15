package com.pte.admin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Rebuildable Admin read projection of safe IAM student display fields.
 * Authentication and identity remain owned by IAM; this entity has no
 * password, login hash, or IAM internal-id field.
 */
@Entity
@Table(name = "student_roster_entries", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_roster_tenant_student",
                columnNames = {"tenant_id", "student_public_id"})
}, indexes = {
        @Index(name = "idx_student_roster_tenant_created_at", columnList = "tenant_id, created_at"),
        @Index(name = "idx_student_roster_tenant_email", columnList = "tenant_id, email"),
        @Index(name = "idx_student_roster_tenant_full_name", columnList = "tenant_id, full_name"),
        @Index(name = "idx_student_roster_tenant_phone", columnList = "tenant_id, phone"),
        @Index(name = "idx_student_roster_tenant_student_code", columnList = "tenant_id, student_code")
})
@Getter
@Setter
@NoArgsConstructor
public class StudentRosterEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_public_id", nullable = false)
    private UUID studentPublicId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "student_code")
    private String studentCode;

    private String phone;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
