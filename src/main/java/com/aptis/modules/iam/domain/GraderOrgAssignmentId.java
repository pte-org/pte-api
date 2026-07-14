package com.aptis.modules.iam.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.Column;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = {"graderId", "organizationId"})
public class GraderOrgAssignmentId implements Serializable {
    // INVARIANT: Never mutate graderId/organizationId after construction — JPA uses embedded PK for identity
    @Column(name = "grader_id")
    private Long graderId;

    @Column(name = "organization_id")
    private Long organizationId;
}
