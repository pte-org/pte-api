package com.aptis.modules.tenancy.domain;

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
@Table(name = "organization")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(callSuper = true)
public class Organization extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String representativeName;

    @Column(nullable = false)
    private String representativeEmail;

    @Column(nullable = false)
    private String representativePhone;

    private String contractCode;

    private String packageName;

    private Integer studentLimit;

    private LocalDate contractStartDate;

    private LocalDate contractEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status = UserStatus.ACTIVE;

    public static Organization create(
            String name,
            String type,
            String address,
            String representativeName,
            String representativeEmail,
            String representativePhone,
            String contractCode,
            String packageName,
            Integer studentLimit,
            LocalDate contractStartDate,
            LocalDate contractEndDate) {
        Organization organization = new Organization();
        organization.setName(name);
        organization.setType(type);
        organization.setAddress(address);
        organization.setRepresentativeName(representativeName);
        organization.setRepresentativeEmail(representativeEmail);
        organization.setRepresentativePhone(representativePhone);
        organization.setContractCode(contractCode);
        organization.setPackageName(packageName);
        organization.setStudentLimit(studentLimit);
        organization.setContractStartDate(contractStartDate);
        organization.setContractEndDate(contractEndDate);
        organization.setStatus(UserStatus.ACTIVE);
        return organization;
    }
}
