package com.aptis.modules.questionbank.service;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.aptis.common.exception.ApiException;
import com.aptis.common.exception.ErrorCode;
import com.aptis.common.security.JwtPrincipal;
import com.aptis.modules.iam.constant.IamApiConstants;
import com.aptis.modules.iam.repository.AdminRepository;
import com.aptis.modules.iam.repository.HostRepository;
import com.aptis.modules.tenancy.repository.OrganizationRepository;

/**
 * Resolves an authenticated principal's public UUID and tenant scope. Shared by
 * question authoring (single CRUD) and bulk import, so the Vendor/Host identity
 * rules have exactly one implementation.
 */
@Component
public class QuestionActorResolver {

    private final AdminRepository adminRepository;
    private final HostRepository hostRepository;
    private final OrganizationRepository organizationRepository;

    public QuestionActorResolver(
            AdminRepository adminRepository,
            HostRepository hostRepository,
            OrganizationRepository organizationRepository) {
        this.adminRepository = adminRepository;
        this.hostRepository = hostRepository;
        this.organizationRepository = organizationRepository;
    }

    public boolean isHost(JwtPrincipal principal) {
        return IamApiConstants.USER_TYPE_HOST.equals(principal.userType());
    }

    /** Resolves the authenticated principal's public UUID from the repository matching their user type. */
    public UUID resolveActorPublicId(JwtPrincipal principal) {
        if (isHost(principal)) {
            return hostRepository.findById(principal.userId())
                    .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                    .getPublicId();
        }
        return adminRepository.findById(principal.userId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                .getPublicId();
    }

    /** Vendor/Admin callers have no tenant (null); Host callers resolve to their Organization's public UUID. */
    public UUID resolveCallerTenantId(JwtPrincipal principal) {
        if (!isHost(principal)) {
            return null;
        }
        return organizationRepository.findById(principal.tenantId())
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND))
                .getPublicId();
    }
}
