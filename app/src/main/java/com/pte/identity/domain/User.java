package com.pte.identity.domain;

import com.pte.shared.domain.BaseEntity;
import com.pte.shared.security.SecurityRoles;
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
 * A person's account. One account can belong to several organizations: tenant-scoped roles live on
 * {@link OrganizationMember}, one row per organization. {@code roles} holds only the account's own roles —
 * platform roles (PLATFORM_ADMIN and friends) and STUDENT for a practice-only account with no organization.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "uk_users_username", columnList = "username", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    /**
     * The login key, unique across the platform. Equals {@code email} for every role except an imported STUDENT,
     * whose roster-generated username is prefixed with its organization's code.
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

    /** The account's own roles (platform roles, or STUDENT for practice-only). Organization roles are on members. */
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

    /** Whether the account itself holds a platform role; such an account must never join an organization. */
    public boolean hasPlatformRole() {
        return roles.stream().anyMatch(role -> SecurityRoles.isPlatformRole(role.name()));
    }
}
