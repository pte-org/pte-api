package com.aptis.modules.iam.domain;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "grader_org_assignment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(of = "id")
public class GraderOrgAssignment {

    @EmbeddedId
    private GraderOrgAssignmentId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grader_id", insertable = false, updatable = false)
    private Grader grader;

    public static GraderOrgAssignment create(Long graderId, Long organizationId) {
        GraderOrgAssignment assignment = new GraderOrgAssignment();
        assignment.id = new GraderOrgAssignmentId(graderId, organizationId);
        return assignment;
    }
}
