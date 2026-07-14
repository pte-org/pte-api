package com.aptis.modules.iam.domain;

import com.aptis.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "organization_student_sequence")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizationStudentSequence extends BaseEntity {

    @Column(nullable = false, unique = true)
    private Long organizationId;

    @Column(nullable = false)
    private Long nextValue;

    public static OrganizationStudentSequence create(Long organizationId, long nextValue) {
        OrganizationStudentSequence sequence = new OrganizationStudentSequence();
        sequence.setOrganizationId(organizationId);
        sequence.setNextValue(nextValue);
        return sequence;
    }

    public long reserve(int count) {
        long startValue = nextValue;
        nextValue += count;
        return startValue;
    }
}
