package com.pte.iam.domain;

import com.pte.common.domain.BaseEntity;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.enums.UserStatus;
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
 * A user belongs to exactly one tenant in Milestone 1 — a full
 * user↔tenant↔role membership join is deferred until multi-tenant users exist
 * (YAGNI); roles are held here as a set scoped to this user's tenant.
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_tenant", columnList = "tenant_id")
})
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Column
    private UUID tenantId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

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
}
