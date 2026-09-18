package com.pte.identity.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A platform user. {@code tenantId} is null for platform-level users
 * (PLATFORM_ADMIN/AUTHOR) and set for tenant-scoped users (host/proctor/student).
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_tenant", columnList = "tenant_id"),
        @Index(name = "uk_users_tenant_username", columnList = "tenant_id, username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /**
     * The actual login key ({@link com.pte.identity.internal.service.AuthService#login}
     * looks up by this, not {@code email}). Equals {@code email} for every role
     * except STUDENT, whose roster-import-generated username is prefixed with
     * its tenant's code instead (plans/quang-tenant-commercialization Phase 8).
     */
    @Column(nullable = false)
    private String username;

    /**
     * No longer globally unique or required at the DB level: two different
     * tenants may create an account with the same email. It remains unique
     * within each tenant, since {@code username} is the tenant-scoped login
     * key.
     */
    @Column
    private String email;

    @Column
    private String fullName;

    @Column
    private UUID tenantId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    /** Profile-only fields (not used for auth) — mainly populated for STUDENT via Host roster import, optional for any role. */
    @Column
    private String studentCode;

    @Column
    private String className;

    @Column
    private String phone;

    @Column
    private LocalDate dateOfBirth;

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED;
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }
}
