package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.UpdateOwnProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.mapper.UserMapper;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Self-service profile operations that do not belong to user administration. */
@Service
public class UserProfileService {

    private final UserRepository userRepository;
    private final TenancyService tenancyService;
    private final AuditLogService auditLogService;

    public UserProfileService(UserRepository userRepository, TenancyService tenancyService,
            AuditLogService auditLogService) {
        this.userRepository = userRepository;
        this.tenancyService = tenancyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse updateOwnProfile(UpdateOwnProfileRequest request, CurrentUser caller) {
        if (caller == null || caller.tenantId() == null || caller.userId() == null
                || !caller.hasRole(Role.HOST_ADMIN.name())) {
            throw new ForbiddenUserManagementException();
        }

        User user = userRepository.findWithLockByPublicIdAndTenantId(caller.userId(), caller.tenantId())
                .orElseThrow(UserNotFoundException::new);
        user.setFullName(request.fullName().trim());
        user.setPhone(normalizeOptional(request.phone()));
        user.setDateOfBirth(request.dateOfBirth());
        User saved = userRepository.save(user);

        auditLogService.record(caller, IdentityConstants.USER_AGGREGATE, saved.getPublicId().toString(),
                IdentityConstants.USER_PROFILE_UPDATED, IdentityConstants.USER_PROFILE_UPDATED_SUMMARY);

        String organizationType = tenancyService.findOrganizationType(saved.getTenantId()).orElse(null);
        return UserMapper.toResponse(saved, organizationType);
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
