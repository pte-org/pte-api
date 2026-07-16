package com.aptis.modules.iam.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.aptis.modules.iam.domain.GraderOrgAssignment;
import com.aptis.modules.iam.domain.GraderOrgAssignmentId;

public interface GraderOrgAssignmentRepository extends JpaRepository<GraderOrgAssignment, GraderOrgAssignmentId> {
    List<GraderOrgAssignment> findByIdGraderId(Long graderId);

    boolean existsByIdGraderIdAndIdOrganizationId(Long graderId, Long organizationId);
}
