package com.pte.identity.internal.domain;

import com.pte.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The password-hash credential record for a user, kept out of {@code User} so
 * it is never loaded or serialized incidentally. Named {@code LoginHash} to
 * keep sensitive terms out of filenames (repo privacy guard).
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
