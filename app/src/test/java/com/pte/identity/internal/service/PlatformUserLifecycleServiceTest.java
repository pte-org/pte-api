package com.pte.identity.internal.service;

import com.pte.identity.UserCredentialsEmailRequestedEvent;
import com.pte.identity.domain.Role;
import com.pte.identity.internal.domain.LoginHash;
import com.pte.identity.internal.dto.request.UpdatePlatformUserProfileRequest;
import com.pte.identity.internal.dto.response.UserResponse;
import com.pte.identity.internal.repository.LoginHashRepository;
import com.pte.identity.internal.repository.UserRepository;
import com.pte.identity.domain.User;
import com.pte.shared.audit.AuditLogService;
import com.pte.shared.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformUserLifecycleServiceTest {

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
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private PlatformUserService platformUserService;
    private PlatformUserLifecycleService service;

    @BeforeEach
    void setUp() {
        platformUserService = new PlatformUserService(userRepository, loginHashRepository, passwordEncoder,
                provisioningHelper, auditLogService, refreshTokenService, eventPublisher);
        service = new PlatformUserLifecycleService(platformUserService, userRepository, loginHashRepository,
                passwordEncoder, refreshTokenService, eventPublisher, auditLogService);
    }

    private CurrentUser admin() {
        return new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
    }

    private User target(UUID publicId) {
        User user = new User();
        user.setId(7L);
        user.setPublicId(publicId);
        user.setUsername("staff@example.test");
        user.setEmail("staff@example.test");
        user.setFullName("Staff User");
        user.setRoles(Set.of(Role.ACADEMIC_STAFF));
        return user;
    }

    private void stubActiveTarget(UUID publicId, User user) {
        when(userRepository.findWithLockByPublicId(publicId)).thenReturn(Optional.of(user));
        when(provisioningHelper.isPlatformTarget(user.getRoles())).thenReturn(true);
    }

    @Test
    void updateProfile_updatesOnlyWhitelistedFieldsAndAudits() {
        CurrentUser caller = admin();
        UUID publicId = UUID.randomUUID();
        User user = target(publicId);
        stubActiveTarget(publicId, user);
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = service.updateProfile(publicId,
                new UpdatePlatformUserProfileRequest("  Updated Name  ", " 0909 ", LocalDate.of(1990, 1, 2)),
                caller);

        assertThat(user.getFullName()).isEqualTo("Updated Name");
        assertThat(user.getPhone()).isEqualTo("0909");
        assertThat(user.getDateOfBirth()).isEqualTo(LocalDate.of(1990, 1, 2));
        assertThat(response.email()).isEqualTo("staff@example.test");
        verify(auditLogService).record(eq(caller), eq("PlatformUser"), eq(publicId.toString()),
                eq("PlatformUserProfileUpdated"), any(String.class));
    }

    @Test
    void resetPassword_generatesAndEmailsTemporaryCredentialWithoutReturningIt() {
        CurrentUser caller = admin();
        UUID publicId = UUID.randomUUID();
        User user = target(publicId);
        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(user.getId());
        loginHash.setHash("old-hash");
        stubActiveTarget(publicId, user);
        when(loginHashRepository.findByUserId(user.getId())).thenReturn(Optional.of(loginHash));
        when(passwordEncoder.encode(any(String.class))).thenReturn("new-hash");
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = service.resetPassword(publicId, caller);

        assertThat(response.mustChangePassword()).isTrue();
        assertThat(response.toString()).doesNotContain("new-hash");
        verify(refreshTokenService).revokeAllForUser(user.getId());
        ArgumentCaptor<UserCredentialsEmailRequestedEvent> event =
                ArgumentCaptor.forClass(UserCredentialsEmailRequestedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().temporaryPassword()).isNotBlank();
        assertThat(event.getValue().temporaryPassword()).isNotEqualTo("new-hash");
        verify(auditLogService).record(eq(caller), eq("PlatformUser"), eq(publicId.toString()),
                eq("PlatformUserPasswordReset"), any(String.class));
    }

    @Test
    void archive_revokesRefreshTokensAndMarksTargetDeleted() {
        CurrentUser caller = admin();
        UUID publicId = UUID.randomUUID();
        User user = target(publicId);
        stubActiveTarget(publicId, user);
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = service.archive(publicId, caller);

        assertThat(response.archived()).isTrue();
        verify(refreshTokenService).revokeAllForUser(user.getId());
        verify(auditLogService).record(eq(caller), eq("PlatformUser"), eq(publicId.toString()),
                eq("PlatformUserArchived"), any(String.class));
    }

    @Test
    void restore_reactivatesOnlySoftDeletedTarget() {
        CurrentUser caller = admin();
        UUID publicId = UUID.randomUUID();
        User user = target(publicId);
        user.setDeleted(true);
        when(userRepository.findWithLockByPublicIdIncludingDeleted(publicId)).thenReturn(Optional.of(user));
        when(provisioningHelper.isPlatformTarget(user.getRoles())).thenReturn(true);
        when(userRepository.save(user)).thenReturn(user);

        UserResponse response = service.restore(publicId, caller);

        assertThat(response.archived()).isFalse();
        verify(auditLogService).record(eq(caller), eq("PlatformUser"), eq(publicId.toString()),
                eq("PlatformUserRestored"), any(String.class));
    }

    @Test
    void archive_cannotTargetCallingAdministrator() {
        CurrentUser caller = admin();
        User user = target(caller.userId());
        stubActiveTarget(caller.userId(), user);

        assertThatThrownBy(() -> service.archive(caller.userId(), caller))
                .isInstanceOf(com.pte.identity.internal.exception.ForbiddenUserManagementException.class);
    }
}
