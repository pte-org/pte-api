package com.aptis.modules.iam.interfaces;

import java.util.List;

import com.aptis.modules.iam.dto.request.admin.CreateGraderRequest;
import com.aptis.modules.iam.dto.request.admin.AssignOrgRequest;
import com.aptis.modules.iam.dto.response.admin.GraderResponse;

public interface GraderAdminOperations {
    GraderResponse createGrader(CreateGraderRequest request);
    List<GraderResponse> listGraders();
    void assignOrgToGrader(Long graderId, Long organizationId);
    void revokeOrgFromGrader(Long graderId, Long organizationId);
    void validateGraderActive(Long graderId);
}
