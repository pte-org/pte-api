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
 * An account. One username belongs to at most one organization ({@code organizationId}, the organization's public
 * id, the same value other modules call {@code tenantId}); platform accounts and free practice students have none.
 * {@code roles} holds every role of the account and {@code status} is its only suspension state. A person who works
 * for two organizations has two accounts. An e-mail is unique per organization (a partial unique index).
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_organization", columnList = "organization_id"),
        @Index(name = "uk_users_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /**
     * The login key, unique across the platform. Organization accounts (students and staff) get a generated
     * {@code {tenant.code}.{random}} username; platform accounts (and staff created before that change) keep their
     * {@code email} as the username.
     */
    @Column(nullable = false)
    private String username;

    @Column
    private String email;

    @Column
    private String fullName;

    /** BCrypt hash of the login secret; null for a passwordless practice account. Never serialized. */
    @Column(name = "credential_hash")
    private String credentialHash;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false)
    private boolean mustChangePassword = false;

    /** The organization this account belongs to (public id); null for platform accounts and free practice students. */
    @Column(name = "organization_id")
    private UUID organizationId;

    /** Every role of the account: platform roles, or the organization roles (HOST_ADMIN, PROCTOR, STUDENT, ...). */
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

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    /** Usable: not suspended. Callers add the tenant check themselves. */
    public boolean isActive() {
        return !isSuspended();
    }

    public void suspend() {
        this.status = UserStatus.SUSPENDED;
    }

    public void reactivate() {
        this.status = UserStatus.ACTIVE;
    }
}
