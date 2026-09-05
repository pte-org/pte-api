package com.pte.iam.service;

import com.pte.common.security.CurrentUser;
import com.pte.iam.domain.LoginHash;
import com.pte.iam.domain.User;
import com.pte.iam.domain.enums.Role;
import com.pte.iam.domain.enums.UserStatus;
import com.pte.iam.domain.exception.DuplicateEmailInBatchException;
import com.pte.iam.domain.exception.ForbiddenPasswordResetException;
import com.pte.iam.domain.exception.UserNotFoundException;
import com.pte.iam.dto.request.BulkCreateUserRow;
import com.pte.iam.dto.request.BulkCreateUsersRequest;
import com.pte.iam.dto.request.CreateUserRequest;
import com.pte.iam.dto.request.ResetPasswordRequest;
import com.pte.iam.dto.response.BulkCreateUsersResponse;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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

    @Mock
    private UserBulkCreateWriter bulkCreateWriter;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, loginHashRepository, passwordEncoder,
                provisioningHelper, outboxWriter, bulkCreateWriter);
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

    @Test
    void create_roundTripsProfileFields() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        CreateUserRequest request = new CreateUserRequest(
                "student@tenant.example", "Student One", "Password123",
                List.of("STUDENT"), null,
                "SC-001", "12A1", "0900000000", LocalDate.of(2008, 5, 1));

        when(userRepository.existsByEmail(request.email())).thenReturn(false);
        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(provisioningHelper.resolveAndAuthorizeRoles(caller, request.roles())).thenReturn(Set.of(Role.STUDENT));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(1L);
            user.setPublicId(UUID.randomUUID());
            return user;
        });

        UserResponse response = userService.create(request, caller);

        assertThat(response.studentCode()).isEqualTo("SC-001");
        assertThat(response.className()).isEqualTo("12A1");
        assertThat(response.phone()).isEqualTo("0900000000");
        assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(2008, 5, 1));
    }

    private BulkCreateUserRow row(String email) {
        return new BulkCreateUserRow(email, "Student " + email, "SC-" + email, "12A1", "0900000000",
                LocalDate.of(2008, 1, 1));
    }

    private UserBulkCreateWriter.Result writerResult(String email, UUID publicId, String password) {
        User user = new User();
        user.setPublicId(publicId);
        user.setEmail(email);
        user.setFullName("Student " + email);
        return new UserBulkCreateWriter.Result(user, password);
    }

    @Test
    void createBulk_createsAllRowsWhenNoneConflict() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("a@tenant.example");
        BulkCreateUserRow rowB = row("b@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByEmailIn(List.of(rowA.email(), rowB.email()))).thenReturn(List.of());
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(rowA.email(), UUID.randomUUID(), "Abcd-2345")))
                .thenReturn(Optional.of(writerResult(rowB.email(), UUID.randomUUID(), "Efgh-6789")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(2);
        assertThat(response.skipped()).isEmpty();
        assertThat(response.created().get(0).generatedPassword()).matches("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$");
    }

    @Test
    void createBulk_skipsExistingEmailRow_butStillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow existing = row("existing@tenant.example");
        BulkCreateUserRow fresh = row("fresh@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(existing, fresh), null);

        User existingUser = new User();
        existingUser.setEmail(existing.email());

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByEmailIn(List.of(existing.email(), fresh.email())))
                .thenReturn(List.of(existingUser));
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.of(writerResult(fresh.email(), UUID.randomUUID(), "Ijkl-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fresh.email());
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).rowIndex()).isEqualTo(0);
        assertThat(response.skipped().get(0).email()).isEqualTo(existing.email());
    }

    @Test
    void createBulk_duplicateEmailWithinBatch_rejectsWholeBatch_noWrites() {
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), UUID.randomUUID(), List.of("HOST_ADMIN"));
        BulkCreateUserRow rowA = row("dup@tenant.example");
        BulkCreateUserRow rowB = row("dup@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(rowA, rowB), null);

        when(provisioningHelper.resolveTargetTenant(any(), any())).thenReturn(UUID.randomUUID());

        assertThatThrownBy(() -> userService.createBulk(request, caller))
                .isInstanceOf(DuplicateEmailInBatchException.class);

        verify(bulkCreateWriter, never()).createOne(any(), any());
    }

    @Test
    void createBulk_writerLosesConcurrentRace_skipsThatRowOnly_stillCreatesOthers() {
        UUID tenantId = UUID.randomUUID();
        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        BulkCreateUserRow raced = row("raced@tenant.example");
        BulkCreateUserRow fine = row("fine@tenant.example");
        BulkCreateUsersRequest request = new BulkCreateUsersRequest(List.of(raced, fine), null);

        when(provisioningHelper.resolveTargetTenant(caller, null)).thenReturn(tenantId);
        when(userRepository.findByEmailIn(List.of(raced.email(), fine.email()))).thenReturn(List.of());
        when(bulkCreateWriter.createOne(any(), eq(tenantId)))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(writerResult(fine.email(), UUID.randomUUID(), "Mnop-2345")));

        BulkCreateUsersResponse response = userService.createBulk(request, caller);

        assertThat(response.created()).hasSize(1);
        assertThat(response.created().get(0).email()).isEqualTo(fine.email());
        assertThat(response.skipped()).hasSize(1);
        assertThat(response.skipped().get(0).email()).isEqualTo(raced.email());
    }

    @Test
    void resetPassword_hostAdmin_succeedsAgainstSameTenantStudent() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User student = userWithId(1L, userPublicId, tenantId);
        student.setRoles(Set.of(Role.STUDENT));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(student));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
    }

    @Test
    void resetPassword_hostAdmin_againstSameTenantHostAdmin_throwsForbidden() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User fellowHostAdmin = userWithId(1L, userPublicId, tenantId);
        fellowHostAdmin.setRoles(Set.of(Role.HOST_ADMIN));

        when(userRepository.findByPublicIdAndTenantId(userPublicId, tenantId)).thenReturn(Optional.of(fellowHostAdmin));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), tenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(ForbiddenPasswordResetException.class);

        verify(loginHashRepository, never()).save(any());
        verify(outboxWriter, never()).write(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    void resetPassword_hostAdmin_againstDifferentTenant_throwsUserNotFound() {
        UUID userPublicId = UUID.randomUUID();
        UUID callerTenantId = UUID.randomUUID();

        when(userRepository.findByPublicIdAndTenantId(userPublicId, callerTenantId)).thenReturn(Optional.empty());

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), callerTenantId, List.of("HOST_ADMIN"));

        assertThatThrownBy(() -> userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller))
                .isInstanceOf(UserNotFoundException.class);

        verify(loginHashRepository, never()).save(any());
    }

    @Test
    void resetPassword_platformAdmin_stillWorksAgainstAnyRoleAnyTenant() {
        UUID userPublicId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User hostAdmin = userWithId(1L, userPublicId, tenantId);
        hostAdmin.setRoles(Set.of(Role.HOST_ADMIN));

        LoginHash loginHash = new LoginHash();
        loginHash.setUserId(1L);
        loginHash.setHash(passwordEncoder.encode("OldPassword123"));

        when(userRepository.findByPublicId(userPublicId)).thenReturn(Optional.of(hostAdmin));
        when(loginHashRepository.findByUserId(1L)).thenReturn(Optional.of(loginHash));

        CurrentUser caller = new CurrentUser(UUID.randomUUID(), null, List.of("PLATFORM_ADMIN"));
        UserResponse response = userService.resetPassword(userPublicId,
                new ResetPasswordRequest("NewPassword456"), caller);

        assertThat(response.publicId()).isEqualTo(userPublicId);
        assertThat(passwordEncoder.matches("NewPassword456", loginHash.getHash())).isTrue();
    }
}
