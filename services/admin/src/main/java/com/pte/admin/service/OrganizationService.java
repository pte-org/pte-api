package com.pte.admin.service;

import com.pte.admin.constant.AdminConstants;
import com.pte.admin.domain.Organization;
import com.pte.admin.domain.Tenant;
import com.pte.admin.domain.enums.OrganizationStatus;
import com.pte.admin.domain.event.OrganizationCreatedEvent;
import com.pte.admin.domain.event.OrganizationReactivatedEvent;
import com.pte.admin.domain.event.OrganizationSuspendedEvent;
import com.pte.admin.domain.exception.OrganizationNameAlreadyUsedException;
import com.pte.admin.domain.exception.OrganizationNotFoundException;
import com.pte.admin.domain.exception.TenantNotFoundException;
import com.pte.admin.dto.request.CreateOrganizationRequest;
import com.pte.admin.dto.response.OrganizationResponse;
import com.pte.admin.mapper.OrganizationMapper;
import com.pte.admin.messaging.outbox.OutboxWriter;
import com.pte.admin.repository.OrganizationRepository;
import com.pte.admin.repository.TenantRepository;
import com.pte.common.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Organization (branch/facility) governance, scoped under a Tenant. Takes
 * {@code CurrentUser caller} on every method now (not strictly needed yet)
 * so Phase 5's audit trail isn't the first place introducing that convention
 * in `services/admin`.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;
    private final TenantRepository tenantRepository;
    private final OutboxWriter outboxWriter;

    public OrganizationService(OrganizationRepository organizationRepository, TenantRepository tenantRepository,
            OutboxWriter outboxWriter) {
        this.organizationRepository = organizationRepository;
        this.tenantRepository = tenantRepository;
        this.outboxWriter = outboxWriter;
    }

    @Transactional
    public OrganizationResponse create(UUID tenantPublicId, CreateOrganizationRequest request, CurrentUser caller) {
        Tenant tenant = tenantRepository.findByPublicId(tenantPublicId)
                .orElseThrow(TenantNotFoundException::new);
        if (organizationRepository.existsByTenant_PublicIdAndNameIgnoreCase(tenantPublicId, request.name())) {
            throw new OrganizationNameAlreadyUsedException();
        }

        Organization organization = new Organization();
        organization.setName(request.name());
        organization.setAddress(request.address());
        organization.setFacilityType(request.facilityType());
        tenant.addOrganization(organization);
        // `tenant` is already managed (loaded above), so saving it would route
        // through entityManager.merge(), which for a transient element newly
        // added to a cascaded collection creates a SEPARATE COPY and persists
        // that instead of `organization` itself — the id would never land on
        // this reference. Persist the new child directly so `organization`
        // itself gets its generated publicId.
        Organization saved = organizationRepository.save(organization);

        outboxWriter.write(AdminConstants.AGGREGATE_ORGANIZATION, saved.getPublicId().toString(),
                AdminConstants.EVENT_ORGANIZATION_CREATED,
                new OrganizationCreatedEvent(saved.getPublicId(), tenantPublicId, saved.getName()),
                tenantPublicId);
        return OrganizationMapper.toResponse(saved, tenantPublicId);
    }

    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(UUID tenantPublicId, CurrentUser caller) {
        if (!tenantRepository.existsByPublicId(tenantPublicId)) {
            throw new TenantNotFoundException();
        }
        return organizationRepository.findByTenant_PublicIdOrderByCreatedAtAsc(tenantPublicId).stream()
                .map(organization -> OrganizationMapper.toResponse(organization, tenantPublicId))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    @Transactional
    public OrganizationResponse suspend(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() == OrganizationStatus.SUSPENDED) {
            return OrganizationMapper.toResponse(organization, tenantPublicId);
        }
        organization.suspend();
        outboxWriter.write(AdminConstants.AGGREGATE_ORGANIZATION, organizationPublicId.toString(),
                AdminConstants.EVENT_ORGANIZATION_SUSPENDED,
                new OrganizationSuspendedEvent(organizationPublicId, tenantPublicId), tenantPublicId);
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    @Transactional
    public OrganizationResponse reactivate(UUID tenantPublicId, UUID organizationPublicId, CurrentUser caller) {
        Organization organization = loadUnderTenant(tenantPublicId, organizationPublicId);
        if (organization.getStatus() == OrganizationStatus.ACTIVE) {
            return OrganizationMapper.toResponse(organization, tenantPublicId);
        }
        organization.reactivate();
        outboxWriter.write(AdminConstants.AGGREGATE_ORGANIZATION, organizationPublicId.toString(),
                AdminConstants.EVENT_ORGANIZATION_REACTIVATED,
                new OrganizationReactivatedEvent(organizationPublicId, tenantPublicId), tenantPublicId);
        return OrganizationMapper.toResponse(organization, tenantPublicId);
    }

    /**
     * Loads an Organization strictly by (tenantPublicId, organizationPublicId)
     * pair — an org id that exists but belongs to a different tenant than the
     * path says is treated as not-found, not silently served, so the URL's
     * nesting is never just decorative.
     */
    private Organization loadUnderTenant(UUID tenantPublicId, UUID organizationPublicId) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(OrganizationNotFoundException::new);
        if (!organization.getTenant().getPublicId().equals(tenantPublicId)) {
            throw new OrganizationNotFoundException();
        }
        return organization;
    }
}
