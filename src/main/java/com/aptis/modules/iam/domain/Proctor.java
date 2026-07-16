package com.aptis.modules.iam.domain;

import com.aptis.common.domain.BaseEntity;
import com.aptis.modules.iam.domain.enums.UserStatus;

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

@Entity
@Table(name = "proctor")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Proctor extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, name = "organization_id")
    private Long organizationId;

    @Column(nullable = false, name = "host_id")
    private String hostId;

    private String fullName;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Proctor create(
            String username,
            Long organizationId,
            String hostId,
            String fullName,
            String passwordHash) {
        Proctor proctor = new Proctor();
        proctor.setUsername(username);
        proctor.setOrganizationId(organizationId);
        proctor.setHostId(hostId);
        proctor.setFullName(fullName);
        proctor.setPasswordHash(passwordHash);
        proctor.setStatus(UserStatus.ACTIVE);
        return proctor;
    }
}
