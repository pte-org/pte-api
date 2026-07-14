package com.aptis.modules.iam.service.admin;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.Grader;
import com.aptis.modules.iam.domain.GraderOrgAssignment;
import com.aptis.modules.iam.domain.GraderOrgAssignmentId;
import com.aptis.modules.iam.domain.enums.UserStatus;
import com.aptis.modules.iam.dto.request.admin.CreateGraderRequest;
import com.aptis.modules.iam.dto.response.admin.GraderResponse;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.GraderAdminOperations;
import com.aptis.modules.iam.repository.GraderOrgAssignmentRepository;
import com.aptis.modules.iam.repository.GraderRepository;

@Service
@Transactional
public class GraderAdminService implements GraderAdminOperations {

    private final GraderRepository graderRepository;
    private final GraderOrgAssignmentRepository assignmentRepository;
    private final CredentialProvisioning credentialProvisioning;

    public GraderAdminService(
            GraderRepository graderRepository,
            GraderOrgAssignmentRepository assignmentRepository,
            CredentialProvisioning credentialProvisioning) {
        this.graderRepository = graderRepository;
        this.assignmentRepository = assignmentRepository;
        this.credentialProvisioning = credentialProvisioning;
    }

    public GraderResponse createGrader(CreateGraderRequest request) {
        if (graderRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, IamApiConstants.GRADER_USERNAME_ALREADY_EXISTS);
        }
        String rawPassword = credentialProvisioning.generateRandomCredential();
        String passwordHash = credentialProvisioning.hashPassword(rawPassword);
        Grader grader = Grader.create(request.getUsername(), passwordHash);
        graderRepository.save(grader);
        return toResponse(grader, Collections.emptyList(), rawPassword);
    }

    @Transactional(readOnly = true)
    public List<GraderResponse> listGraders() {
        List<Grader> graders = graderRepository.findAll();
        Map<Long, List<Long>> orgsByGrader = assignmentRepository.findAll().stream()
                .collect(Collectors.groupingBy(
                        a -> a.getId().getGraderId(),
                        Collectors.mapping(a -> a.getId().getOrganizationId(), Collectors.toList())));
        return graders.stream()
                .map(g -> toResponse(g, orgsByGrader.getOrDefault(g.getId(), Collections.emptyList()), null))
                .toList();
    }

    public void assignOrgToGrader(Long graderId, Long organizationId) {
        if (!graderRepository.existsById(graderId)) {
            throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, IamApiConstants.GRADER_NOT_FOUND);
        }
        try {
            assignmentRepository.save(GraderOrgAssignment.create(graderId, organizationId));
        } catch (DataIntegrityViolationException e) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, IamApiConstants.GRADER_ORG_ASSIGNMENT_ALREADY_EXISTS);
        }
    }

    public void revokeOrgFromGrader(Long graderId, Long organizationId) {
        assignmentRepository.deleteById(new GraderOrgAssignmentId(graderId, organizationId));
    }

    @Transactional(readOnly = true)
    public void validateGraderActive(Long graderId) {
        Grader grader = graderRepository.findById(graderId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, IamApiConstants.GRADER_NOT_FOUND));
        if (!UserStatus.ACTIVE.equals(grader.getStatus())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, IamApiConstants.GRADER_NOT_ACTIVE);
        }
    }

    private GraderResponse toResponse(Grader grader, List<Long> orgIds, String rawPassword) {
        return new GraderResponse(grader.getId(), grader.getUsername(), grader.getStatus(), orgIds, rawPassword);
    }
}
