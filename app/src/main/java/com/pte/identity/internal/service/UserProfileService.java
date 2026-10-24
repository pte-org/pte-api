package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.OrganizationMember;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.UpdateOwnProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service profile operations that do not belong to user administration. */
@Service
public class UserProfileService {

    private final OrganizationMemberDirectory memberDirectory;
    private final TenancyService tenancyService;
    private final AuditLogService auditLogService;

    public UserProfileService(OrganizationMemberDirectory memberDirectory, TenancyService tenancyService,
            AuditLogService auditLogService) {
        this.memberDirectory = memberDirectory;
        this.tenancyService = tenancyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse updateOwnProfile(UpdateOwnProfileRequest request, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || caller.userId() == null
                || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }

        OrganizationMember member = memberDirectory.requireMemberForUpdate(caller.tenantId(), caller.userId());
        User user = member.getUser();
        user.setFullName(request.fullName().trim());
        user.setPhone(normalizeOptional(request.phone()));
        user.setDateOfBirth(request.dateOfBirth());

        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, user.getPublicId().toString(),
                IdentityConstants.USER_PROFILE_UPDATED, IdentityConstants.USER_PROFILE_UPDATED_SUMMARY);

        String organizationType = tenancyService.findOrganizationType(caller.tenantId()).orElse(null);
        return UserMapper.toResponse(member, organizationType);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
