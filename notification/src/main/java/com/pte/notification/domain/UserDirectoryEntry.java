package com.pte.notification.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * notification's own local email directory — built by consuming iam's {@code
 * UserCreated}, never a sync call to iam (same denormalize-via-event pattern
 * as every other cross-service reference in this codebase). {@code roles} is
 * a plain string set (not iam's {@code Role} enum) — only used to filter
 * "every HOST_ADMIN in this tenant" for violation fan-out, not for authz.
 */
@Entity
@Table(name = "user_directory_entries")
@Getter
@Setter
@NoArgsConstructor
public class UserDirectoryEntry extends BaseEntity {

    @Column(name = "user_public_id", nullable = false, unique = true)
    private UUID userPublicId;

    @Column(nullable = false)
    private String email;

    @Column
    private UUID tenantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_directory_roles", joinColumns = @JoinColumn(name = "user_directory_entry_id"))
    @Column(name = "role", nullable = false)
    private Set<String> roles = new HashSet<>();
}
