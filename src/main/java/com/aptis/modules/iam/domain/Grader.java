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
@Table(name = "grader")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Grader extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false, name = "password_hash")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Grader create(String username, String passwordHash) {
        Grader grader = new Grader();
        grader.setUsername(username);
        grader.setPasswordHash(passwordHash);
        grader.setStatus(UserStatus.ACTIVE);
        return grader;
    }
}
