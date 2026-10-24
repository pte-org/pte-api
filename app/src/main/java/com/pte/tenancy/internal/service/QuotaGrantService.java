package com.pte.tenancy.internal.service;

import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.domain.Organization;
import com.pte.tenancy.domain.QuotaGrant;
import com.pte.tenancy.internal.constant.TenancyConstants;
import com.pte.tenancy.internal.dto.request.GrantQuotaRequest;
import com.pte.tenancy.internal.dto.response.QuotaGrantResponse;
import com.pte.tenancy.internal.exception.QuotaConflictException;
import com.pte.tenancy.internal.exception.TenantNotFoundException;
import com.pte.tenancy.internal.mapper.QuotaGrantMapper;
import com.pte.tenancy.internal.repository.OrganizationRepository;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Seat grants for an organization: a pure audit trail of traceable allocation (no billing). The grant is appended
 * to the Organization aggregate, so the raised limit and its history commit together.
 */
@Service
public class QuotaGrantService {

    private final OrganizationRepository organizationRepository;

    public QuotaGrantService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public QuotaGrantResponse grant(UUID organizationPublicId, GrantQuotaRequest request, CurrentUser caller) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(TenantNotFoundException::new);
        QuotaGrant grant = organization.grantQuota(
                request.packageName(), request.amount(), caller.userId(), request.note());
        try {
            // Flush now so a lost update from a concurrent grant on the same organization throws here,
            // inside this method, where it can still be translated into a clean 409.
            organizationRepository.saveAndFlush(organization);
        } catch (OptimisticLockingFailureException ex) {
            throw new QuotaConflictException();
        }
        return QuotaGrantMapper.toResponse(grant, organizationPublicId);
    }

    /** Billing activation path for a capacity plan; records a system actor in the history. */
    @Transactional
    public QuotaGrantResponse grantForSystem(UUID organizationPublicId, int amount, String note) {
        return grant(organizationPublicId,
                new GrantQuotaRequest(TenancyConstants.SYSTEM_QUOTA_PACKAGE, amount, note),
                new CurrentUser(TenancyConstants.SYSTEM_ACTOR_USER_ID, null, List.of(TenancyConstants.SYSTEM_ROLE)));
    }

    @Transactional(readOnly = true)
    public List<QuotaGrantResponse> history(UUID organizationPublicId) {
        Organization organization = organizationRepository.findByPublicId(organizationPublicId)
                .orElseThrow(TenantNotFoundException::new);
        return organization.getQuotaGrants().reversed().stream()
                .map(grant -> QuotaGrantMapper.toResponse(grant, organizationPublicId))
                .toList();
    }
}
