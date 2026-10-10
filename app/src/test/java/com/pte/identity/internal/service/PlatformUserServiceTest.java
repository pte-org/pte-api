package com.pte.identity.internal.service;

import com.pte.identity.domain.Role;
import com.pte.identity.domain.User;
import com.pte.identity.internal.constant.IdentityConstants;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.PlatformUserCreateRequest;
import com.pte.identity.internal.dto.request.PlatformUserRoleUpdateRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.exception.ForbiddenUserManagementException;
import com.pte.identity.internal.exception.ForbiddenRoleAssignmentException;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformUserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserProvisioningHelper provisioningHelper;

    @Mock
    private AuditLogService auditLogService;

    private PlatformUserService service;

    @BeforeEach
    void setUp() {
        service = new PlatformUserService(userRepository, loginHashRepository, passwordEncoder,
                provisioningHelper, auditLogService);
    }

    private CurrentUser admin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    private User platformUser(UUID publicId, Role role) {
        User user = new User();
        user.setId(7L);
        user.setPublicId(publicId);
        user.setUsername("academic@example.com");
        user.setEmail("academic@example.com");
        user.setFullName("Academic User");
        user.setRoles(Set.of(role));
        return user;
    }

    @Test
    void create_platformUser_isTenantlessAndAudited() {
        CurrentUser caller = admin();
        PlatformUserCreateRequest request = new PlatformUserCreateRequest(
                "academic@example.com", "Academic User", "Password123!", List.of("ACADEMIC_STAFF"));
        User saved = platformUser(UUID.randomUUID(), Role.ACADEMIC_STAFF);
        when(provisioningHelper.resolvePlatformRoles(caller, request.roles()))
                .thenReturn(Set.of(Role.ACADEMIC_STAFF));
        when(userRepository.existsByUsernameAndTenantId(request.email(), null)).thenReturn(false);
        when(passwordEncoder.encode(request.password())).thenReturn("hash");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(saved);

        UserResponse response = service.create(request, caller);

        assertThat(response.tenantId()).isNull();
        assertThat(response.roles()).containsExactly("ACADEMIC_STAFF");
        verify(loginHashRepository).save(any(LoginHash.class));
        verify(auditLogService).record(eq(caller), eq("PlatformUser"),
                eq(saved.getPublicId().toString()), eq("PlatformUserCreated"), any(String.class));
    }

    @Test
    void create_nonAdmin_isRejectedBeforePersistence() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("ACADEMIC_MANAGER"));
        PlatformUserCreateRequest request = new PlatformUserCreateRequest(
                "academic@example.com", "Academic User", "Password123!", List.of("ACADEMIC_STAFF"));

        assertThatThrownBy(() -> service.create(request, caller))
                .isInstanceOf(ForbiddenUserManagementException.class);
        verify(auditLogService).recordFailure(eq(caller), eq(IdentityConstants.PLATFORM_USER_AGGREGATE),
                eq("authorization"), eq(IdentityConstants.PLATFORM_AUTHORIZATION_DENIED), any(String.class));
    }

    @Test
    void list_normalizesSearchBeforeQueryingRepository() {
        CurrentUser caller = admin();
        when(provisioningHelper.platformRoleValues())
                .thenReturn(Set.of(Role.PLATFORM_MANAGER, Role.ACADEMIC_MANAGER, Role.ACADEMIC_STAFF));
        when(userRepository.findPageForPlatformUsers(anySet(), isNull(), isNull(), eq("academic staff"),
                any(Pageable.class))).thenReturn(Page.empty());

        service.list(0, 20, "  Academic Staff  ", "", "", caller);

        verify(userRepository).findPageForPlatformUsers(anySet(), isNull(), isNull(), eq("academic staff"),
                any(Pageable.class));
    }

    @Test
    void updateRoles_cannotTargetCallerOrGrantPlatformAdmin() {
        CurrentUser caller = admin();
        UUID targetId = UUID.randomUUID();
        User target = platformUser(targetId, Role.ACADEMIC_STAFF);
        when(userRepository.findWithLockByPublicId(targetId)).thenReturn(Optional.of(target));
        when(provisioningHelper.isPlatformTarget(target.getRoles())).thenReturn(true);
        when(provisioningHelper.resolvePlatformRoles(caller, List.of("PLATFORM_ADMIN")))
                .thenThrow(new ForbiddenRoleAssignmentException());

        assertThatThrownBy(() -> service.updateRoles(targetId,
                new PlatformUserRoleUpdateRequest(List.of("PLATFORM_ADMIN")), caller))
                .isInstanceOf(ForbiddenRoleAssignmentException.class);
        verify(auditLogService).recordFailure(eq(caller), eq(IdentityConstants.PLATFORM_USER_AGGREGATE),
                eq(IdentityConstants.PLATFORM_ROLE_ASSIGNMENT_AGGREGATE_ID),
                eq(IdentityConstants.PLATFORM_AUTHORIZATION_DENIED), any(String.class));
    }

    @Test
    void updateRoles_cannotTargetTheCallingPlatformAdmin() {
        CurrentUser caller = admin();
        User target = platformUser(caller.userId(), Role.ACADEMIC_STAFF);
        when(userRepository.findWithLockByPublicId(caller.userId())).thenReturn(Optional.of(target));
        when(provisioningHelper.isPlatformTarget(target.getRoles())).thenReturn(true);

        assertThatThrownBy(() -> service.updateRoles(caller.userId(),
                new PlatformUserRoleUpdateRequest(List.of("ACADEMIC_MANAGER")), caller))
                .isInstanceOf(ForbiddenUserManagementException.class);
        verify(auditLogService).recordFailure(eq(caller), eq(IdentityConstants.PLATFORM_USER_AGGREGATE),
                eq(caller.userId().toString()), eq(IdentityConstants.PLATFORM_AUTHORIZATION_DENIED),
                any(String.class));
    }

    @Test
    void suspend_platformUser_usesLockedTargetAndAuditsLifecycle() {
        CurrentUser caller = admin();
        UUID targetId = UUID.randomUUID();
        User target = platformUser(targetId, Role.ACADEMIC_STAFF);
        when(userRepository.findWithLockByPublicId(targetId)).thenReturn(Optional.of(target));
        when(provisioningHelper.isPlatformTarget(target.getRoles())).thenReturn(true);

        UserResponse response = service.suspend(targetId, caller);

        assertThat(response.status()).isEqualTo("SUSPENDED");
        verify(auditLogService).record(eq(caller), eq("PlatformUser"), eq(targetId.toString()),
                eq("PlatformUserSuspended"), any(String.class));
    }
}
