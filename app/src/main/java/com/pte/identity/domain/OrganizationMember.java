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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * An account's membership in one organization, carrying the roles it holds there (HOST_ADMIN, PROCTOR, EXAMINER,
 * STUDENT). {@code organizationId} is the organization's public id, the same value other modules call
 * {@code tenantId}. A removed member is soft-deleted.
 */
@Entity
@Table(name = "organization_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_organization_members_user_org",
                columnNames = {"user_id", "organization_id"}),
        indexes = @Index(name = "idx_organization_members_org", columnList = "organization_id"))
@Getter
@Setter
@NoArgsConstructor
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    /** Suspension inside this organization only; the account itself can be suspended separately. */
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "organization_member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = new HashSet<>();

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean isSuspended() {
        return status == UserStatus.SUSPENDED || user.isSuspended();
    }

    /** Usable in this organization: neither the membership nor the account is suspended. */
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
