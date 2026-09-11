package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.TenantStatus;
import com.pte.admin.domain.event.TenantBrandingUpdatedEvent;
import com.pte.admin.domain.event.TenantOnboardedEvent;
import com.pte.admin.domain.event.TenantReactivatedEvent;
import com.pte.admin.domain.event.TenantSuspendedEvent;
import com.pte.admin.domain.exception.TenantNameAlreadyUsedException;
import com.pte.admin.domain.exception.TenantNotFoundException;
import com.pte.admin.dto.request.OnboardTenantRequest;
import com.pte.admin.dto.request.UpdateBrandingRequest;
import com.pte.admin.dto.response.TenantResponse;
import com.pte.admin.mapper.TenantMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.TenantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Tenant governance (onboard/suspend/read). Onboarding emits {@code TenantOnboarded}
 * to the outbox in the same TX so iam can seed the tenant registry (ADR-002).
 * Nothing on a data-plane runtime path depends on this service (ADR-001).
 */
@Service
public class TenantLifecycleService {

    private final TenantRepository tenantRepository;
    private final OutboxWriter outboxWriter;

    public TenantLifecycleService(TenantRepository tenantRepository, OutboxWriter outboxWriter) {
        this.tenantRepository = tenantRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public TenantResponse onboard(OnboardTenantRequest request) {
        if (tenantRepository.existsByName(request.name())) {
            throw new TenantNameAlreadyUsedException();
        }
        Tenant tenant = new Tenant();
        tenant.setName(request.name());
        tenant.setOrganizationType(request.organizationType());
        tenant.setPackageName(request.packageName());
        tenant.setStudentLimit(request.studentLimit());
        Tenant saved = tenantRepository.save(tenant);

        outboxWriter.write(AdminConstants.AGGREGATE_TENANT, saved.getPublicId().toString(),
                AdminConstants.EVENT_TENANT_ONBOARDED,
                new TenantOnboardedEvent(saved.getPublicId(), saved.getName(), saved.getOrganizationType()),
                saved.getPublicId());
        return TenantMapper.toResponse(saved);
    }

    @Transactional
    public TenantResponse suspend(UUID publicId) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.suspend();
        outboxWriter.write(AdminConstants.AGGREGATE_TENANT, tenant.getPublicId().toString(),
                AdminConstants.EVENT_TENANT_SUSPENDED,
                new TenantSuspendedEvent(tenant.getPublicId()), tenant.getPublicId());
        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse reactivate(UUID publicId) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        if (tenant.getStatus() == TenantStatus.ACTIVE) {
            return TenantMapper.toResponse(tenant);
        }
        tenant.reactivate();
        outboxWriter.write(AdminConstants.AGGREGATE_TENANT, tenant.getPublicId().toString(),
                AdminConstants.EVENT_TENANT_REACTIVATED,
                new TenantReactivatedEvent(tenant.getPublicId()), tenant.getPublicId());
        return TenantMapper.toResponse(tenant);
    }

    @Transactional
    public TenantResponse updateBranding(UUID publicId, UpdateBrandingRequest request) {
        Tenant tenant = tenantRepository.findByPublicId(publicId)
                .orElseThrow(TenantNotFoundException::new);
        tenant.updateBranding(request.logoUrl(), request.primaryColor());
        outboxWriter.write(AdminConstants.AGGREGATE_TENANT, tenant.getPublicId().toString(),
                AdminConstants.EVENT_TENANT_BRANDING_UPDATED,
                new TenantBrandingUpdatedEvent(tenant.getPublicId(), tenant.getLogoUrl(), tenant.getPrimaryColor()),
                tenant.getPublicId());
        return TenantMapper.toResponse(tenant);
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
