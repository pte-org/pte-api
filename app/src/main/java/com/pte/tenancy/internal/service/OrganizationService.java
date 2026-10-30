package com.pte.tenancy.internal.service;

import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.enums.OrganizationStatus;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.UpdateOrganizationProfileRequest;
import com.pte.tenancy.internal.dto.response.OrganizationResponse;
import com.pte.tenancy.internal.exception.OrganizationNotFoundException;
import com.pte.tenancy.internal.exception.TenantNameAlreadyUsedException;
import com.pte.tenancy.internal.mapper.OrganizationMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Read and profile operations on organizations. Platform callers see every approved organization; a Host sees
 * and edits only the organization named in its token. Governance (onboarding, suspension) is
 * {@link OrganizationLifecycleService}.
 */
@Service
public class OrganizationService {

    private static final int SEARCH_LIMIT = 20;

    private final OrganizationRepository organizationRepository;
    private final AuditLogService auditLogService;
    private final VietnamLocationService vietnamLocationService;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this(organizationRepository, null, null);
    }

    public OrganizationService(OrganizationRepository organizationRepository, AuditLogService auditLogService) {
        this(organizationRepository, auditLogService, null);
    }

    @Autowired
    public OrganizationService(OrganizationRepository organizationRepository, AuditLogService auditLogService,
            VietnamLocationService vietnamLocationService) {
        this.organizationRepository = organizationRepository;
        this.auditLogService = auditLogService;
        this.vietnamLocationService = vietnamLocationService;
    }

    /** Platform callers (no organization in the token) get every approved organization; others get their own. */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(CurrentUser caller) {
        return list(caller, null);
    }

    /**
     * Same, with an optional platform-only lookup by code or tax-code prefix or exact contact email, capped at
     * {@value #SEARCH_LIMIT} rows. A host's {@code q} is ignored: it only ever sees its own organization.
     */
    @Transactional(readOnly = true)
    public List<OrganizationResponse> list(CurrentUser caller, String q) {
        String term = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        if (caller.isPlatformUser() && !term.isEmpty()) {
            // Wildcards are stripped so a typed % or _ cannot widen the prefix match.
            String prefix = term.replace("%", "").replace("_", "").replace("\\", "") + "%";
            return organizationRepository.searchApproved(prefix, term, PageRequest.of(0, SEARCH_LIMIT)).stream()
                    .map(OrganizationMapper::toResponse).toList();
        }
        if (caller.isPlatformUser()) {
            return organizationRepository.findAllApproved().stream().map(OrganizationMapper::toResponse).toList();
        }
        return organizationRepository.findByPublicId(caller.tenantId()).stream()
                .filter(this::isApproved)
                .map(OrganizationMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationResponse get(UUID organizationPublicId, CurrentUser caller) {
        return OrganizationMapper.toResponse(loadVisibleTo(organizationPublicId, caller));
    }

    @Transactional
    public OrganizationResponse updateProfile(UUID organizationPublicId, UpdateOrganizationProfileRequest request,
            CurrentUser caller) {
        UUID ownOrganizationId = caller.tenantId();
        if (ownOrganizationId == null || !ownOrganizationId.equals(organizationPublicId)) {
            throw new OrganizationNotFoundException();
        }
        Organization organization = organizationRepository.findWithLockByPublicId(organizationPublicId)
                .filter(this::isApproved)
                .orElseThrow(OrganizationNotFoundException::new);
        String name = request.name().trim();
        if (!name.equals(organization.getName())
                && organizationRepository.existsByNameAndStatusNot(name, OrganizationStatus.REJECTED)) {
            throw new TenantNameAlreadyUsedException();
        }
        organization.setName(name);
        organization.setAddress(normalizeOptional(request.address()));
        VietnamLocationService.ResolvedLocation location = resolveLocation(request);
        organization.setProvinceCode(location.provinceCode());
        organization.setProvinceName(location.provinceName());
        organization.setWardCode(location.wardCode());
        organization.setWardName(location.wardName());
        organization.setFacilityType(request.facilityType());
        auditLogService.record(caller, TenancyConstants.AGGREGATE_ORGANIZATION,
                organizationPublicId.toString(), TenancyConstants.EVENT_ORGANIZATION_UPDATED,
                TenancyConstants.AUDIT_ORGANIZATION_UPDATED_SUMMARY);
        return OrganizationMapper.toResponse(organization);
    }

    /** A Host may only reach its own organization; a cross-organization id is "not found", never "forbidden". */
    private Organization loadVisibleTo(UUID organizationPublicId, CurrentUser caller) {
        if (!caller.isPlatformUser() && !organizationPublicId.equals(caller.tenantId())) {
            throw new OrganizationNotFoundException();
        }
        return organizationRepository.findByPublicId(organizationPublicId)
                .filter(this::isApproved)
                .orElseThrow(OrganizationNotFoundException::new);
    }

    private boolean isApproved(Organization organization) {
        return !organization.isDeleted() && (organization.getStatus() == OrganizationStatus.ACTIVE
                || organization.getStatus() == OrganizationStatus.SUSPENDED);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private VietnamLocationService.ResolvedLocation resolveLocation(UpdateOrganizationProfileRequest request) {
        String provinceCode = normalizeOptional(request.provinceCode());
        String wardCode = normalizeOptional(request.wardCode());
        if (vietnamLocationService == null) {
            if (provinceCode != null || wardCode != null) {
                throw new IllegalStateException("Vietnam location service is not configured");
            }
            return VietnamLocationService.ResolvedLocation.empty();
        }
        return vietnamLocationService.resolve(provinceCode, wardCode);
    }
}
