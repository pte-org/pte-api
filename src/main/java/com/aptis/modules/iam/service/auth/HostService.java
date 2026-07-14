package com.aptis.modules.iam.service.auth;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.modules.iam.domain.Host;
import com.aptis.modules.iam.dto.request.admin.CreateHostRequest;
import com.aptis.modules.iam.dto.response.admin.HostResponse;
import com.aptis.modules.iam.interfaces.CredentialProvisioning;
import com.aptis.modules.iam.interfaces.HostManagement;
import com.aptis.modules.iam.repository.HostRepository;
import com.aptis.modules.tenancy.domain.Organization;
import com.aptis.modules.tenancy.repository.OrganizationRepository;

@Service
@Transactional
public class HostService implements HostManagement {

    private final CredentialProvisioning credentialProvisioning;
    private final HostRepository hostRepository;
    private final OrganizationRepository organizationRepository;

    public HostService(
            CredentialProvisioning credentialProvisioning,
            HostRepository hostRepository,
            OrganizationRepository organizationRepository) {
        this.credentialProvisioning = credentialProvisioning;
        this.hostRepository = hostRepository;
        this.organizationRepository = organizationRepository;
    }

    @Override
    public HostResponse createHost(CreateHostRequest request) {
        validateHostIsUnique(request);

        Organization organization = organizationRepository.save(createOrganization(request));
        String initialPassword = credentialProvisioning.generateRandomCredential();
        Host host = Host.create(
                request.code(),
                request.name(),
                organization.getId(),
                request.contactEmail(),
                credentialProvisioning.hashPassword(initialPassword));

        Host savedHost = hostRepository.save(host);
        return toResponse(savedHost, organization, initialPassword);
    }

    private Organization createOrganization(CreateHostRequest request) {
        return Organization.create(
                request.organizationName(),
                request.organizationType(),
                request.address(),
                request.representativeName(),
                request.contactEmail(),
                request.representativePhone(),
                request.contractCode(),
                request.packageName(),
                request.studentLimit(),
                request.contractStartDate(),
                request.contractEndDate());
    }

    private HostResponse toResponse(
            Host host,
            Organization organization,
            String initialPassword) {
        return new HostResponse(
                host.getId(),
                host.getCode(),
                host.getName(),
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getAddress(),
                organization.getRepresentativeName(),
                host.getContactEmail(),
                organization.getRepresentativePhone(),
                organization.getContractCode(),
                organization.getPackageName(),
                organization.getStudentLimit(),
                organization.getContractStartDate(),
                organization.getContractEndDate(),
                host.getCreatedAt(),
                initialPassword);
    }

    private void validateHostIsUnique(CreateHostRequest request) {
        if (hostRepository.findByContactEmail(request.contactEmail()).isPresent()
                || hostRepository.findByCode(request.code()).isPresent()
                || organizationRepository.findByRepresentativeEmail(request.contactEmail()).isPresent()
                || contractCodeExists(request.contractCode())) {
            throw new ApiException(ErrorCode.RESOURCE_CONFLICT);
        }
    }

    private boolean contractCodeExists(String contractCode) {
        return contractCode != null
                && !contractCode.isBlank()
                && organizationRepository.findByContractCode(contractCode).isPresent();
    }
}
