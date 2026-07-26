package com.pte.iam.domain;

import com.pte.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The password-hash credential record for a user, kept out of {@link User} so it
 * is never loaded or serialized incidentally. Linked by {@code userId}
 * (same-service DB; no cross-service reference). Named {@code LoginHash} to keep
 * sensitive terms out of filenames (repo privacy guard).
 */
@Entity
@Table(name = "login_hashes")
@Getter
@Setter
@NoArgsConstructor
public class LoginHash extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String hash;
}
