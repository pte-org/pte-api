package com.pte.iam.service;

import com.pte.common.security.CurrentUser;
import com.pte.iam.domain.LoginHash;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.enums.UserStatus;
import com.pte.iam.domain.exception.UserNotFoundException;
import com.pte.iam.dto.request.ResetPasswordRequest;
import com.pte.iam.dto.response.UserResponse;
import com.pte.iam.messaging.outbox.OutboxWriter;
import com.pte.iam.repository.LoginHashRepository;
import com.pte.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private LoginHashRepository loginHashRepository;

    @Mock
    private UserProvisioningHelper provisioningHelper;

    @Mock
    private OutboxWriter outboxWriter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, loginHashRepository, passwordEncoder,
                provisioningHelper, outboxWriter);
    }

    private User userWithId(Long id, UUID publicId, UUID tenantId) {
        User user = new User();
        user.setId(id);
        user.setPublicId(publicId);
        user.setEmail("host@tenant.example");
        user.setFullName("Host Admin");
        user.setTenantId(tenantId);
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(Role.HOST_ADMIN));
        return user;
    }

    @Test
    void resetPassword_overwritesHash_soOldPasswordNoLongerMatchesAndNewOneDoes() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        User user = userWithId(1L, userPublicId, tenantId);

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(user));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(callerId, null, List.of("PLATFORM_ADMIN"));
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("OldPassword123", loginHash.getHash())).isFalse();
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
        verify(loginHashRepository, times(1)).save(loginHash);
        verify(outboxWriter, times(1)).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void resetPassword_userNotFound_throwsAndNeverTouchesLoginHashOrOutbox() {
        UUID userPublicId = UUID.randomUUID();
        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));

        assertThatThrownBy(() ->
                userService.resetPassword(userPublicId, new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(loginHashRepository, never()).save(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void listForTenant_returnsOnlyThatTenantsUsers() {
        UUID tenantId = UUID.randomUUID();
        User user = userWithId(1L, UUID.randomUUID(), tenantId);
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of(user));

        List<UserResponse> result = userService.listForTenant(tenantId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void listForTenant_noUsers_returnsEmptyList() {
        UUID tenantId = UUID.randomUUID();
        when(userRepository.findByTenantId(tenantId)).thenReturn(List.of());

        assertThat(userService.listForTenant(tenantId)).isEmpty();
    }
}
