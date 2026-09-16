package com.pte.tenancy.internal.service;

import com.pte.tenancy.domain.Tenant;
import com.pte.tenancy.domain.enums.TenantStatus;
import com.pte.tenancy.internal.exception.TenantCodeAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.dto.request.OnboardTenantRequest;
import com.pte.tenancy.internal.dto.request.UpdateBrandingRequest;
import com.pte.tenancy.internal.dto.response.TenantResponse;
import com.pte.tenancy.internal.mapper.TenantMapper;
import com.pte.tenancy.internal.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Tenant governance (onboard/suspend/read). */
@Service
public class TenantLifecycleService {

    private final TenantRepository tenantRepository;

    public TenantLifecycleService(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Transactional
    public TenantResponse onboard(OnboardTenantRequest request) {
        if (tenantRepository.existsByCode(request.code())) {
            throw new TenantCodeAlreadyUsedException();
        }
        if (tenantRepository.existsByName(request.name())) {
            throw new TenantNameAlreadyUsedException();
        }
        Tenant tenant = new Tenant();
        tenant.setCode(request.code());
        tenant.setName(request.name());
        tenant.setOrganizationType(request.organizationType());
        tenant.setPackageName(request.packageName());
        tenant.setStudentLimit(request.studentLimit());
        Tenant saved = tenantRepository.save(tenant);        return TenantMapper.toResponse(saved);
    }

    @Transactional
    public TenantResponse suspend(UUID publicId) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.suspend();        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse reactivate(UUID publicId) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.reactivate();        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateBranding(UUID publicId, UpdateBrandingRequest request) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        tenant.updateBranding(request.logoUrl(), request.primaryColor());        return TenantMapper.toResponse(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponse get(UUID publicId) {
        return TenantMapper.toResponse(tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new));
    }

    @Transactional(readOnly = true)
    public List<TenantResponse> list() {
        return tenantRepository.findAll().stream().map(TenantMapper::toResponse).toList();
    }
}
