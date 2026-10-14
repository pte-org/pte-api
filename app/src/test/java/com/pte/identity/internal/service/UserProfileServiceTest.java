package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.domain.UserStatus;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.dto.request.UpdateOwnProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.UserNotFoundException;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import com.pte.tenancy.TenancyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TenancyService tenancyService;

    @Mock
    private AuditLogService auditLogService;

    private UserProfileService userProfileService;

    @BeforeEach
    void setUp() {
        userProfileService = new UserProfileService(userRepository, tenancyService, auditLogService);
    }

    @Test
    void updateOwnProfile_updatesEditableFields_andKeepsTenantScope() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User user = userWithId(userPublicId, tenantId);
        CurrentUser caller = new CurrentUser(userPublicId, tenantId, List.of("HOST_ADMIN"));
        UpdateOwnProfileRequest request = new UpdateOwnProfileRequest(
                "  Updated Host  ", " 0900000000 ", LocalDate.of(1990, 5, 20));

        when(userRepository.findWithLockByPublicIdAndTenantId(userPublicId, tenantId))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(tenancyService.findOrganizationType(tenantId)).thenReturn(Optional.of("SCHOOL"));

        UserResponse response = userProfileService.updateOwnProfile(request, caller);

        assertThat(user.getFullName()).isEqualTo("Updated Host");
        assertThat(user.getPhone()).isEqualTo("0900000000");
        assertThat(user.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(response.fullName()).isEqualTo("Updated Host");
        assertThat(response.phone()).isEqualTo("0900000000");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1990, 5, 20));
        assertThat(response.organizationType()).isEqualTo("SCHOOL");
        verify(userRepository).findWithLockByPublicIdAndTenantId(userPublicId, tenantId);
        verify(auditLogService).record(eq(caller), eq(IdentityConstants.USER_AGGREGATE),
                eq(userPublicId.toString()), eq(IdentityConstants.USER_PROFILE_UPDATED),
                eq(IdentityConstants.USER_PROFILE_UPDATED_SUMMARY));
    }

    @Test
    void updateOwnProfile_rejectsNonHostCaller_beforeLoadingUser() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("PROCTOR"));

        assertThatThrownBy(() -> userProfileService.updateOwnProfile(
                new UpdateOwnProfileRequest("Name", null, null), caller))
                .isInstanceOf(ForbiddenUserManagementException.class);

        verify(userRepository, never()).findWithLockByPublicIdAndTenantId(any(), any());
    }

    @Test
    void updateOwnProfile_throwsWhenCallerUserIsNotInTheirTenant() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(userPublicId, tenantId, List.of("HOST_ADMIN"));
        when(userRepository.findWithLockByPublicIdAndTenantId(userPublicId, tenantId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userProfileService.updateOwnProfile(
                new UpdateOwnProfileRequest("Name", null, null), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(userRepository, never()).save(any());
    }

    private User userWithId(UUID publicId, UUID tenantId) {
        User user = new User();
        user.setId(1L);
        user.setPublicId(publicId);
        user.setUsername("host@tenant.example");
        user.setEmail("host@tenant.example");
        user.setFullName("Host Admin");
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.HOST_ADMIN));
        return user;
    }
}
