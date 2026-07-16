package com.aptis.modules.iam.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.domain.Proctor;
import com.aptis.modules.iam.dto.request.host.CreateProctorRequest;
import com.aptis.modules.iam.dto.response.host.ProctorResponse;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.HostProctorOperations;
import com.aptis.modules.iam.repository.ProctorRepository;

@Service
@Transactional
public class HostProctorService implements HostProctorOperations {

    private final ProctorRepository proctorRepository;
    private final CredentialProvisioning credentialProvisioning;

    public HostProctorService(ProctorRepository proctorRepository, CredentialProvisioning credentialProvisioning) {
        this.proctorRepository = proctorRepository;
        this.credentialProvisioning = credentialProvisioning;
    }

    @Override
    public ProctorResponse createProctor(Long organizationId, String hostId, CreateProctorRequest request) {
        if (proctorRepository.existsByUsername(request.getUsername())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT, IamApiConstants.PROCTOR_USERNAME_ALREADY_EXISTS);
        }
        String rawPassword = credentialProvisioning.generateRandomCredential();
        String passwordHash = credentialProvisioning.hashPassword(rawPassword);
        Proctor proctor = Proctor.create(
                request.getUsername(), organizationId, hostId, request.getFullName(), passwordHash);
        proctorRepository.save(proctor);
        return toResponse(proctor, rawPassword);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProctorResponse> listProctors(Long organizationId) {
        return proctorRepository.findByOrganizationId(organizationId).stream()
                .map(p -> toResponse(p, null))
                .toList();
    }

    private ProctorResponse toResponse(Proctor proctor, String rawPassword) {
        return new ProctorResponse(
                proctor.getId(), proctor.getUsername(), proctor.getFullName(), proctor.getStatus(), rawPassword);
    }
}
