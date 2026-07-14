package com.aptis.modules.tenancy.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.aptis.modules.tenancy.domain.Organization;
import com.aptis.modules.tenancy.dto.TenantResponse;
import com.aptis.modules.tenancy.interfaces.TenantOperations;
import com.aptis.modules.tenancy.repository.OrganizationRepository;

@Service
public class TenantService implements TenantOperations {

    private final OrganizationRepository organizationRepository;

    public TenantService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Override
    public List<TenantResponse> listTenants() {
        return organizationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public TenantResponse getTenant(Long id) {
        return organizationRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow();
    }

    private TenantResponse toResponse(Organization organization) {
        return new TenantResponse(
                organization.getId(),
                organization.getName(),
                organization.getType(),
                organization.getAddress(),
                organization.getRepresentativeName(),
                organization.getRepresentativeEmail(),
                organization.getRepresentativePhone(),
                organization.getContractCode(),
                organization.getPackageName(),
                organization.getStudentLimit(),
                organization.getContractStartDate(),
                organization.getContractEndDate(),
                organization.getStatus());
    }
}
