package com.aptis.modules.iam.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import com.aptis.common.domain.BaseEntity;
import com.aptis.modules.iam.domain.enums.UserStatus;

@Entity
@Table(name = "admin")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Admin extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Admin create(
            String email,
            String name,
            String passwordHash) {
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setName(name);
        admin.setPasswordHash(passwordHash);
        admin.setStatus(UserStatus.ACTIVE);
        return admin;
    }
}
