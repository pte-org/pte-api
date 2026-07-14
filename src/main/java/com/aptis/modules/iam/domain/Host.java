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
@Table(name = "host")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Host extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false, unique = true)
    private String contactEmail;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private Boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Host create(
            String code,
            String name,
            Long organizationId,
            String contactEmail,
            String passwordHash) {
        Host host = new Host();
        host.setCode(code);
        host.setName(name);
        host.setOrganizationId(organizationId);
        host.setContactEmail(contactEmail);
        host.setPasswordHash(passwordHash);
        host.setMustChangePassword(true);
        host.setStatus(UserStatus.ACTIVE);
        return host;
    }
}
