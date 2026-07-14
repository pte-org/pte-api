package com.aptis.modules.iam.domain;

import java.time.LocalDate;

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
@Table(name = "student")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Student extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private Long organizationId;

    @Column(nullable = false)
    private String hostId;

    private String fullName;

    private String studentCode;

    private String className;

    private String email;

    private String phone;

    private LocalDate dateOfBirth;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Student create(
            String username,
            Long organizationId,
            String hostId,
            String fullName,
            String studentCode,
            String className,
            String email,
            String phone,
            String passwordHash) {
        Student student = new Student();
        student.setUsername(username);
        student.setOrganizationId(organizationId);
        student.setHostId(hostId);
        student.setFullName(fullName);
        student.setStudentCode(studentCode);
        student.setClassName(className);
        student.setEmail(email);
        student.setPhone(phone);
        student.setPasswordHash(passwordHash);
        student.setStatus(UserStatus.ACTIVE);
        return student;
    }
}
